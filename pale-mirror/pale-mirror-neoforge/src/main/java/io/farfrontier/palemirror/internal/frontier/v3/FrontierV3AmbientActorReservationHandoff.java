package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.HivePhysiologySupport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Bounded exact ambient-to-scene hand-off, separate from ordinary ambient body behavior. */
final class FrontierV3AmbientActorReservationHandoff {
    private FrontierV3AmbientActorReservationHandoff() { }

    static void run(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState initialState) {
        int transferred = 0;
        FrontierWorldState reservationState = initialState;
        java.util.Set<SubjectId> reservedActors = reservedActors(runtime, reservationState);
        for (SubjectId actorId : initialState.actorLocations().keySet().stream().sorted().toList()) {
            if (transferred >= 16) return;
            FrontierWorldState state = runtime.decodedState().orElse(null);
            if (state == null) return;
            if (state != reservationState) {
                reservationState = state;
                reservedActors = reservedActors(runtime, reservationState);
            }
            if (!reservedActors.contains(actorId)) continue;
            var location = state.actorLocations().get(actorId);
            if (location == null || location.condition().status() != ActorLifeStatus.ALIVE
                    || !HivePhysiologySupport.permitsAmbientLease(state, actorId)) continue;
            AmbientActorLease lease = state.ambientLeases().get(actorId);
            if (lease == null || lease.status() == AmbientLeaseStatus.CLOSED) continue;
            if (lease.status() == AmbientLeaseStatus.HOT) {
                Entity body = level.getEntity(FrontierV3AmbientActorExecutor.entityId(state, actorId));
                if (body instanceof Mob mob && FrontierV3AmbientActorExecutor.owned(mob, actorId,
                        FrontierV3AmbientActorExecutor.bioform(state, actorId)) && FrontierV3AmbientActorExecutor.drain(runtime, mob)) transferred++;
                continue;
            }
            if (lease.status() == AmbientLeaseStatus.PREPARED && FrontierV3AmbientActorExecutor.abandonPreparedForReservation(
                    level, runtime, state, actorId, lease)) transferred++;
        }
    }

    static java.util.Set<SubjectId> reservedActors(FrontierV3ServerRuntime<?, ?> runtime, FrontierWorldState state) {
        return FrontierV3AmbientActorCaches.reservationAdmission(runtime, state).reservedActorIds();
    }
}
