package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.security.GuardMission;
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
            var contact=io.farfrontier.palemirror.frontier.v3.model.security.GuardResponse.contact(state,owner,tick).orElse(null);
            if(contact==null || !contact.target().equals(mission.response().orElseThrow())
                    || mission.station().manhattanDistance(new SurfaceAnchor(contact.position()))>io.farfrontier.palemirror.frontier.v3.model.security.GuardResponse.MAX_CONTACT_STATION_DISTANCE)
                throw new IllegalArgumentException("guard response lacks its exact current bounded contact");
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
        return state.withStrategicPlans(state.strategicPlans().withGuard(owner.withMission(mission)));
    }
}
