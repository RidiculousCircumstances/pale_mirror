package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;

/** Hive-owned passive eligibility, distinct from physical availability and group execution. */
public final class HivePresencePolicy {
    private HivePresencePolicy() { }
    public static boolean permits(FrontierWorldState state, SubjectId actorId) {
        var actor = state.actorLocations().get(actorId);
        if (actor == null || actor.kind() != ActorKind.BIOFORM || actor.condition().status() != ActorLifeStatus.ALIVE)
            return false;
        var profile = FrontierWorldStateSupport.bioform(state.bootstrap(), state.hiveColony(), actorId);
        return profile.hiveId().equals(state.bootstrap().hive().id())
                && permits(state.hiveColony(), state.physicalDeltas(), actorId);
    }
    static boolean permits(HiveColony colony, Map<BlockPosition, PhysicalDelta> deltas, SubjectId actorId) {
        return HivePhysiologySupport.permitsAmbientLease(colony, deltas, actorId)
                && colony.mobilizations().values().stream().noneMatch(mobilization ->
                !mobilization.status().terminal() && mobilization.memberIds().contains(actorId));
    }
}
