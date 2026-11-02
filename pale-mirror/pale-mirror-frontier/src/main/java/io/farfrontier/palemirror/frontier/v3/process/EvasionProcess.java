package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;
/** Addressed safety continuation. No global population polling or native position writer. */
public final class EvasionProcess {
    public static final String REVIEW="frontier.evasion.review";
    public static ScheduledAction at(SubjectId actor,long tick){return new ScheduledAction(new ScheduleId("schedule:safety-"+actor.value().replace(':','-')),new SimInstant(tick),1,actor,REVIEW,1);}
    public static ProposedEvent wake(SubjectId actor,long tick){return new ProposedEvent(actor,new ScheduleEffect.ReconsiderationRequested(at(actor,tick+1)));}
    public static List<ProposedEvent> review(FrontierWorldState state,ScheduledAction action,long tick){
        var actor=action.subject();var value=state.combat().evasions().get(actor);var events=new ArrayList<ProposedEvent>();
        if(value==null)return List.of(new ProposedEvent(actor,new ScheduleEffect.Cancelled(action.id())));
        var rules=state.bootstrap().ruleset().evasion();
        if(tick>=value.quietUntil() || state.actorLocations().get(actor).condition().status()!=ActorLifeStatus.ALIVE){
            if(value.execution().isPresent()){
                var checkpoint=ActorMovementProcess.ownerTransitionCheckpoint(state,value.execution().orElseThrow(),tick);
                if(checkpoint instanceof ActivityInterruptionPlanner.Waiting)return later(action,tick+rules.reviewTicks());
                var ready=((ActivityInterruptionPlanner.Ready)checkpoint).validate(state,actor);events.addAll(ready.events());state=ready.following();
            }
            events.add(new ProposedEvent(actor,new EvasionEvent.Ended(actor,value.id(),value.revision())));
            events.add(new ProposedEvent(actor,new ScheduleEffect.Cancelled(action.id())));events.add(ResidentActivityProcess.wakeAfterActivity(actor,tick));return List.copyOf(events);
        }
        if(value.execution().isEmpty()){
            // A requested purpose asks its family to yield; readiness alone does not release physical scene custody.
            if(ActorExecutionCoordinator.sceneOwns(state,actor))return later(action,tick+rules.reviewTicks());
            var retained=state.actorExecutions().actors().get(actor);
            if(retained!=null && retained.current().isPresent()){
                var current=retained.current().orElseThrow();var checkpoint=ActorMovementProcess.ownerTransitionCheckpoint(state,current,tick);
                if(checkpoint instanceof ActivityInterruptionPlanner.Waiting)return later(action,tick+rules.reviewTicks());
                var ready=((ActivityInterruptionPlanner.Ready)checkpoint).validate(state,actor);state=ready.following();events.addAll(ready.events());
                var owner=ActorExecutionComposition.CAPABILITIES.require(current.activityKind());
                if(owner.interruption()==ActorActivityCapability.Interruption.TERMINAL_ONLY || !owner.checkpoint(state,current).ready()
                        || ActorInventoryInteractionFences.pendingOwner(state,actor).isPresent() || retained.continuations().size()>=ActorExecution.MAX_CONTINUATIONS)
                    return later(action,tick+rules.reviewTicks());
            }
            var execution=state.actorExecutions().next(actor,ActorActivityKind.SAFETY,value.id());var started=new EvasionEvent.Started(actor,value.id(),execution);
            state=EvasionTransitions.apply(state,actor,started,tick);events.add(new ProposedEvent(actor,started));value=state.combat().evasions().get(actor);
        }
        var movement=state.actorMovements().get(actor);
        var origin=ActorMovementProcess.bodyAt(state,actor,tick).supportingSurface();
        if(movement!=null){
            // Keep a useful leg. Only new close danger at its retained destination warrants retargeting.
            var retainedStation=value.station().orElseThrow();
            if(value.threatPosition().filter(p->EvasionDestinations.distance(p,retainedStation)<rules.dangerRadius()).isEmpty()){
                events.addAll(later(action,tick+rules.reviewTicks()));return List.copyOf(events);
            }
            var checkpoint=ActorMovementProcess.ownerTransitionCheckpoint(state,value.execution().orElseThrow(),tick);
            if(checkpoint instanceof ActivityInterruptionPlanner.Waiting){events.addAll(later(action,tick+rules.reviewTicks()));return List.copyOf(events);}
            var ready=((ActivityInterruptionPlanner.Ready)checkpoint).validate(state,actor);state=ready.following();events.addAll(ready.events());
        }
        if(value.threatPosition().filter(p->EvasionDestinations.distance(origin,p)>=rules.safeRadius()).isPresent()){
            events.addAll(later(action,Math.min(value.quietUntil(),tick+rules.reviewTicks())));return List.copyOf(events);
        }
        var knowledge=KnownPedestrianRouteKnowledge.forPublicJourney(state,value.scope(),List.of()).forActor(state,actor);
        var safetyState=state;
        var candidates=PedestrianInteractionStations.within(knowledge.geometry(),origin,rules.searchRadius(),Set.of()).stream()
                .filter(knowledge::clearStation).filter(p->!FacilityPedestrianContacts.of(safetyState.bootstrap()).noParking().contains(p.support()) && ServiceBoundaryComposition.turnoverPoint(safetyState,p).isEmpty()).toList();
        var protection=GuardEquipment.in(state,value.scope()).stream().filter(g->g.phase()==GuardDuty.Phase.ON_DUTY)
                .map(g->safetyState.actorLocations().get(g.execution().actorId()).supportingSurface()).toList();
        var ranked=EvasionDestinations.rank(origin,value.threatPosition(),candidates,protection,rules);int requests=0;
        for(var point:ranked){
            try{
                var route=knowledge.plannedBetween(origin,point);var threat=value.threatPosition();
                if(threat.isPresent() && route.stream().anyMatch(p->EvasionDestinations.distance(p,threat.orElseThrow())+1<EvasionDestinations.distance(origin,threat.orElseThrow())))continue;
            }
            catch(KnownPedestrianNavigation.RouteUnavailable waiting){if(waiting.status()!=PedestrianRouteResult.Status.NO_PATH && ++requests>=rules.routeRequests())break;continue;}
var selected=new EvasionEvent.Destination(value.execution().orElseThrow(),value.revision(),point);state=EvasionTransitions.apply(state,actor,selected,tick);events.add(new ProposedEvent(actor,selected));
                value=state.combat().evasions().get(actor);
            var next=new ActorMovement(value.order(),tick,new ActorMovementContext.EvasionLeg(value.id(),value.revision()),value.execution().orElseThrow());events.addAll(ActorMovementProcess.planStart(next));break;
        }
        events.addAll(later(action,tick+rules.reviewTicks()));return List.copyOf(events);
    }
    private static List<ProposedEvent> later(ScheduledAction action,long tick){return List.of(new ProposedEvent(action.subject(),new ScheduleEffect.Rescheduled(action.id(),at(action.subject(),tick))));}
    private EvasionProcess(){}
}
