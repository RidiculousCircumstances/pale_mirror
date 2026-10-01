package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.AmbientBodyConfirmationProcess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** Checkpoints only causal service-boundary changes, not every movement tick. */
final class FrontierV3AmbientServiceOccupancy {
    private FrontierV3AmbientServiceOccupancy() { }
    static boolean observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                           FrontierWorldState state, SubjectId actorId, Mob body, AmbientActorLease lease) {
        var supported = FrontierV3BodyObservation.capture(body).supportedBody();
        ActorLocation actor = state.actorLocations().get(actorId);
        if (supported.isEmpty() || actor == null || !AmbientBodyConfirmationProcess.serviceOccupancyChanged(state,
                actorId, actor.body(), supported.orElseThrow())) return false;
        var result = FrontierV3AmbientActorExecutor.submit(runtime, "ambient-service-occupancy", actorId.value(),
                new AmbientBodyConfirmed(actorId, lease.revision(), AmbientBodyConfirmed.Boundary.SERVICE_OCCUPANCY,
                        actor.body(), supported.orElseThrow()));
        FrontierV3DiagnosticTrace.record(level.getServer(), "service-access:" + actorId.value(),
                "ambient_service_occupancy", actorId, result);
        return result instanceof CommandResult.Accepted;
    }
}
