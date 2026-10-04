package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorExecutionComposition;
import io.farfrontier.palemirror.frontier.v3.model.AmbientActorLease;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import net.minecraft.world.entity.Mob;
import java.util.Optional;

/** Presentation is evidence, not an owner; registered activity strategies authorize its goal. */
final class FrontierV3AmbientActuation {
    private FrontierV3AmbientActuation() { }

    static Optional<FrontierV3ActorActuation> capture(FrontierWorldState basis,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, AmbientActorLease lease) {
        var retained = basis.actorExecutions().actors().get(lease.actorId());
        var execution = retained == null ? null : retained.current().orElse(null);
        return execution == null ? Optional.empty() : capture(basis, runtime, body, lease, execution);
    }

    static Optional<FrontierV3ActorActuation> capture(FrontierWorldState basis,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, AmbientActorLease lease,
            ActorExecutionId execution) {
        if (!permitted(basis, execution, lease)) return Optional.empty();
        try {
            return Optional.of(FrontierV3ActorActuation.capture(basis, body, execution,
                    () -> runtime.decodedState().filter(current -> permitted(current, execution, lease))));
        } catch (IllegalArgumentException denied) {
            return Optional.empty();
        }
    }

    private static boolean permitted(FrontierWorldState state, ActorExecutionId execution, AmbientActorLease lease) {
        try {
            ActorExecutionComposition.CAPABILITIES.requireAmbientMotion(state, execution, lease);
            return true;
        } catch (IllegalArgumentException denied) {
            return false;
        }
    }
}
