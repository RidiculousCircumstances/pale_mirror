package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.AmbientLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.AmbientBodyConfirmed;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Arms the normal physical provider only after the just-admitted lease is durably HOT. */
final class FrontierV3AmbientHotAdmission {
    private FrontierV3AmbientHotAdmission() { }

    static void confirmAndArm(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SubjectId actorId) {
        confirm(level, runtime, actorId, true);
    }
    static void confirmForHandoff(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SubjectId actorId) {
        confirm(level, runtime, actorId, false);
    }
    private static void confirm(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SubjectId actorId, boolean arm) {
        FrontierWorldState current = runtime.decodedState().orElse(null);
        if (current == null) return;
        AmbientActorLease lease = current.ambientLeases().get(actorId);
        if (lease == null || lease.status() != AmbientLeaseStatus.PREPARED) return;
        Entity admitted = level.getEntity(FrontierV3AmbientActorExecutor.entityId(current, actorId));
        if (!(admitted instanceof Mob admittedBody)
                || !FrontierV3AmbientActorExecutor.owned(admittedBody, actorId, FrontierV3AmbientActorExecutor.bioform(current, actorId))) return;
        var supported = FrontierV3BodyObservation.capture(admittedBody).supportedBody();
        if (supported.isEmpty()) return;
        var bodyId = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(current, actorId);
        if (!FrontierV3ActorBodyController.inspectCurrent(level, runtime, admittedBody)) return;
        var result = FrontierV3AmbientActorExecutor.submit(runtime, "ambient-hot-body-confirmed", actorId.value(),
                new AmbientBodyConfirmed(actorId, lease.revision(), AmbientBodyConfirmed.Boundary.ADMISSION,
                        lease.handoffBody(), supported.orElseThrow(), bodyId));
        FrontierV3DiagnosticTrace.record(level.getServer(), "ambient-admission:" + actorId.value(),
                "ambient_body_admitted", actorId, result);
        current = runtime.decodedState().orElse(null);
        if (current == null) return;
        lease = current.ambientLeases().get(actorId);
        Entity entity = level.getEntity(FrontierV3AmbientActorExecutor.entityId(current, actorId));
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT || !(entity instanceof Mob body)
                || !FrontierV3AmbientActorExecutor.owned(body, actorId, FrontierV3AmbientActorExecutor.bioform(current, actorId))) return;
        if (!arm) { FrontierV3AmbientActorExecutor.holdForPreLeaseHandoff(body); return; }
        FrontierV3AmbientActorCaches.rememberObserved(runtime, actorId, body, FrontierV3AmbientPendingAdmissions.MAX_ENTRIES);
        FrontierV3ScenePresentation.applyAmbientActorPresentation(body, current, actorId, FrontierV3AmbientActorExecutor.bioform(current, actorId));
        if (!FrontierV3HotScoutObservation.observe(level, runtime, current, actorId, body, lease)) {
            FrontierV3AmbientActorExecutor.pursueLocalGoal(level, runtime, current, actorId, body, lease);
        }
    }
}
