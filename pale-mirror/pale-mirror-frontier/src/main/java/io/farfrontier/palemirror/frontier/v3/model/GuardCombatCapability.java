package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import java.util.*;
/** Guard-owned adapter. The combat module never inspects guard appointments or settlement politics. */
final class GuardCombatCapability implements CombatCapability {
    public ActorActivityKind kind(){return ActorActivityKind.GUARD;}
    public void requireReference(FrontierWorldState state,ActorExecutionId execution){GuardAuthority.require(state,execution);}
    public void requirePending(FrontierWorldState state,MeleeStrike strike){GuardAuthority.requirePendingCombat(state.strategicPlans(),strike);}
    public List<CombatOrder> orders(FrontierWorldState state,long tick){
        return state.strategicPlans().guards().values().stream().flatMap(duty->current(state,duty.execution(),tick).stream()).toList();
    }
    public Optional<CombatOrder> current(FrontierWorldState state,ActorExecutionId execution,long tick){
        var duty=GuardAuthority.require(state,execution);
        if(!SecurityDispatch.eligible(state,duty))return Optional.empty();
        return GuardResponse.contact(state,duty,tick).map(contact->{
            var response=state.security().response(duty.id()).orElseThrow();
            var observer=ActorMovementProjection.bodyAt(state,execution.actorId(),tick).supportingSurface();
            return new CombatOrder(execution,duty.revision(),duty.weapon(),new CombatPartyId(duty.home()),response.engagement(),new SurfaceAnchor(contact.position()),
                    contact.atTick(),state.bootstrap().ruleset().security().contactLifetime(),observer,state.actorLocations().get(execution.actorId()).navigationProfile(),
                    duty.mission().filter(m->m.response().equals(Optional.of(contact.target()))).map(GuardMission::station));
        });
    }
    public boolean permitsApproach(FrontierWorldState state,ActorMovement movement){
        var duty=GuardAuthority.require(state,movement.executionId());
        return movement.context() instanceof ActorMovementContext.GuardMissionLeg leg && leg.dutyId().equals(duty.id())
                && leg.revision()==duty.revision() && movement.order().equals(duty.missionOrder())
                && duty.mission().filter(m->m.stage()==GuardMission.Stage.INTERCEPT).flatMap(GuardMission::response)
                    .filter(target->state.security().response(duty.id()).filter(response->response.contact().target().equals(target)).isPresent()).isPresent();
    }
    public boolean permitsManeuverInterruption(FrontierWorldState state,ActorMovement movement,CombatOrder order){
        var duty=GuardAuthority.require(state,movement.executionId());
        return order.execution().equals(duty.execution()) && duty.phase()==GuardDuty.Phase.ON_DUTY
            && movement.context() instanceof ActorMovementContext.GuardMissionLeg leg && leg.dutyId().equals(duty.id())
            && leg.revision()==duty.revision() && movement.order().equals(duty.missionOrder())
            && state.security().response(duty.id()).filter(response->response.contact().target().equals(order.target())).isPresent();
    }
    public KnownPedestrianRouteKnowledge maneuverKnowledge(FrontierWorldState state,ActorExecutionId execution){
        var duty=GuardAuthority.require(state,execution);return KnownPedestrianRouteKnowledge.forPublicJourney(state,duty.home(),List.of()).forActor(state,execution.actorId());
    }
    public io.farfrontier.palemirror.frontier.v3.model.navigation.TravelPace walkingPace(FrontierWorldState state,ActorExecutionId execution){
        GuardAuthority.require(state,execution);return ResidentWalkingPace.read(state,execution.actorId(),io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode.WALK);
    }
    public OptionalLong harmedAt(FrontierWorldState state,CombatOrder order){
        var duty=GuardAuthority.require(state,order.execution());return state.security().awareness().harmsIn(duty.home()).stream()
            .filter(h->h.key().victim().equals(order.execution().actorId()) && h.key().attacker().equals(order.target())).mapToLong(h->h.atTick()).max();
    }
    public Set<MeleeManeuver.Kind> maneuvers(){return java.util.EnumSet.allOf(MeleeManeuver.Kind.class);}
    public List<ProposedEvent> wake(FrontierWorldState state,ActorExecutionId execution,long tick){
        var duty=GuardAuthority.require(state,execution);
        return List.of(GuardContinuation.wake(duty.id(),tick),SecurityReview.wake(duty.home(),tick));
    }
    public Optional<Fact> targetEnded(FrontierWorldState state,MeleeStrike strike){
        var duty=GuardAuthority.require(state,strike.attacker().execution());
        var contact=state.security().contact(duty.home(),strike.target());
        return contact==null?Optional.empty():Optional.of(new Fact(duty.home(),new SecurityEvent.ContactEnded(strike.attacker(),duty.home(),strike.target(),contact.revision())));
    }
}
