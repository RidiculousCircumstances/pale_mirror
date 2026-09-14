package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseTransition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Arms the normal physical provider only after the just-admitted lease is durably HOT. */
final class FrontierV3AmbientHotAdmission {
    private FrontierV3AmbientHotAdmission() { }

    static void confirmAndArm(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SubjectId actorId) {
        FrontierWorldState current = runtime.decodedState().orElse(null);
        if (current == null) return;
        AmbientActorLease lease = current.ambientLeases().get(actorId);
        if (lease == null || lease.status() != AmbientLeaseStatus.PREPARED) return;
        FrontierV3AmbientActorExecutor.submit(runtime, "ambient-hot", actorId.value(), new AmbientLeaseTransition(actorId, AmbientLeaseStatus.HOT));
        current = runtime.decodedState().orElse(null);
        if (current == null) return;
        lease = current.ambientLeases().get(actorId);
        Entity entity = level.getEntity(FrontierV3AmbientActorExecutor.entityId(current, actorId));
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT || !(entity instanceof Mob body)
                || !FrontierV3AmbientActorExecutor.owned(body, actorId, FrontierV3AmbientActorExecutor.bioform(current, actorId))) return;
        FrontierV3AmbientActorCaches.rememberObserved(runtime, actorId, body, FrontierV3AmbientPendingAdmissions.MAX_ENTRIES);
        FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(body);
        FrontierV3ScenePresentation.applyAmbientActorPresentation(body, current, actorId, FrontierV3AmbientActorExecutor.bioform(current, actorId));
        if (!FrontierV3HotScoutObservation.observe(level, runtime, current, actorId, body, lease)) {
            FrontierV3AmbientActorExecutor.pursueLocalGoal(level, runtime, current, actorId, body, lease);
        }
    }
}
