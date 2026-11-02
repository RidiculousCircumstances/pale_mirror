package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.security.GuardMission;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import java.util.*;
public record GuardMissionSelected(SubjectId duty, long expectedRevision, GuardMission mission) implements FrontierPayload {
    public static final String TYPE = "frontier.guard.mission_selected";
    public GuardMissionSelected { Objects.requireNonNull(duty); Objects.requireNonNull(mission);
        if (expectedRevision < 1) throw new IllegalArgumentException("invalid guard mission predecessor"); }
    public String type() { return TYPE; }
    public FrontierWorldState apply(FrontierWorldState state, SubjectId subject, long tick) {
        var owner = Objects.requireNonNull(state.strategicPlans().guards().get(duty), "guard appointment");
        if (!subject.equals(duty) || owner.revision()!=expectedRevision || owner.phase()!=GuardDuty.Phase.ON_DUTY
                || !GuardEquipment.held(state, owner) || GuardAuthority.pending(state, owner)
                || state.actorMovements().containsKey(owner.execution().actorId())) throw new IllegalArgumentException("stale or unsettled guard mission");
        state.actorExecutions().requireCurrent(owner.execution());
        var target=io.farfrontier.palemirror.frontier.v3.model.security.ProtectionObligations.read(state,owner.home()).stream()
                .filter(value->value.kind()==mission.targetKind() && value.id().equals(mission.target())).findFirst()
                .orElseThrow(()->new IllegalArgumentException("guard mission lacks its declared protected target"));
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(),mission.station().support());
        if(mission.response().isPresent()) {
            var allocated=state.security().response(owner.id()).orElseThrow(()->new IllegalArgumentException("guard has no response allocation"));
            var contact=state.security().contacts().get(allocated.contact());
            boolean intercept=mission.stage()==GuardMission.Stage.INTERCEPT;
            var order=intercept?CombatComposition.CAPABILITIES.current(state,owner.execution(),tick):Optional.<CombatOrder>empty();
            boolean legalMelee=order.filter(value->MeleePositions.weapon(state.combat(),value)
                    .filter(profile->MeleePositions.legal(value,profile,mission.station())).isPresent()).isPresent();
            int radius=state.bootstrap().ruleset().security().searchRadius();
            if(!io.farfrontier.palemirror.frontier.v3.model.security.GuardResponse.permitsStation(state,owner,mission.station())
                    || contact==null || !contact.target().equals(mission.response().orElseThrow())
                    || intercept && io.farfrontier.palemirror.frontier.v3.model.security.GuardResponse.contact(state,owner,tick).isEmpty()
                    || !intercept && !io.farfrontier.palemirror.frontier.v3.model.security.GuardResponse.investigating(state,owner,tick)
                    || intercept && !legalMelee
                    || !intercept && mission.station().manhattanDistance(new SurfaceAnchor(contact.position()))>radius)
                throw new IllegalArgumentException("guard response lacks its exact allocated bounded contact");
            if(mission.search().isPresent()) {
                var search=mission.search().orElseThrow();var previous=owner.mission().orElseThrow();
                if(search.sightingRevision()!=contact.revision() || search.probe()>state.bootstrap().ruleset().security().searchProbes())
                    throw new IllegalArgumentException("guard search exceeds its probe budget");
                if(previous.stage()!=GuardMission.Stage.SEARCH || previous.search().orElseThrow().sightingRevision()!=search.sightingRevision()) {
                    if(search.probe()!=0 || search.pauseUntil()!=0
                            || mission.station().manhattanDistance(new SurfaceAnchor(contact.position()))>
                                io.farfrontier.palemirror.frontier.v3.model.security.GuardResponse.MAX_CONTACT_STATION_DISTANCE
                            || !search.visited().equals(List.of(mission.station())))throw new IllegalArgumentException("search lost its last-known approach");
                } else {
                    var before=previous.search().orElseThrow();var visited=new ArrayList<>(before.visited());
                    if(mission.station().equals(previous.station())) {
                        if(search.probe()!=before.probe() || !search.visited().equals(before.visited()) || before.pauseUntil()!=0
                                || search.pauseUntil()!=tick+state.bootstrap().ruleset().security().searchPauseTicks()
                                || !mission.station().equals(state.actorLocations().get(owner.execution().actorId()).supportingSurface()))
                            throw new IllegalArgumentException("guard search has no bounded observed pause");
                    } else {
                        visited.add(mission.station());
                        if(search.probe()!=before.probe()+1 || search.pauseUntil()!=0 || !search.visited().equals(visited)
                                || before.pauseUntil()==0 || tick<before.pauseUntil()
                                || !previous.station().equals(state.actorLocations().get(owner.execution().actorId()).supportingSurface()))
                            throw new IllegalArgumentException("guard search advanced without its completed observation pause");
                    }
                }
            }
        } else if(!io.farfrontier.palemirror.frontier.v3.model.security.SecurityCoverage.permitsPost(target,mission.station()))
            throw new IllegalArgumentException("guard post lies outside its protected observation area");
        if(mission.response().isPresent() && (owner.mission().isEmpty()
                || !owner.mission().orElseThrow().post().equals(mission.resume().orElseThrow())))
            throw new IllegalArgumentException("guard response changed its retained patrol post");
        if(mission.watchUntil()>0 && (mission.watchUntil()!=Math.addExact(tick,state.bootstrap().ruleset().security().watchTicks())
                || owner.mission().isEmpty() || owner.mission().orElseThrow().watchUntil()!=0 || owner.mission().orElseThrow().response().isPresent()
                || !owner.mission().orElseThrow().post().equals(mission.post())
                || !owner.missionOrder().arrivedAt(state.actorLocations().get(owner.execution().actorId()).supportingSurface())))
            throw new IllegalArgumentException("guard watch lacks exact arrival at its retained post");
        var combat=state.combat();
        if(mission.stage()==GuardMission.Stage.INTERCEPT){
            var order=CombatComposition.CAPABILITIES.current(state,owner.execution(),tick).orElseThrow();
            MeleePositions.requireClaim(state,order,mission.station(),tick);
            combat=combat.positioned(order,Optional.of(mission.station()),MeleePosition.Reason.RESERVED,tick,state.bootstrap().ruleset().melee());
        }else combat=combat.releasePosition(owner.id());
        return state.withChanges(FrontierWorldStateUpdate.begin().strategicPlans(state.strategicPlans().withGuard(owner.withMission(mission))).combat(combat));
    }
}
