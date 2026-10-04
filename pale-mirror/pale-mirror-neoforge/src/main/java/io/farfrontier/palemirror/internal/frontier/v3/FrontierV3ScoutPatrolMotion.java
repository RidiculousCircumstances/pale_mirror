package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.HiveScoutPatrolProcess;
import net.minecraft.world.entity.Mob;
import java.util.Optional;

/** Scout adapter captures its semantic goal before physical motion or observation. */
final class FrontierV3ScoutPatrolMotion {
    private FrontierV3ScoutPatrolMotion() { }
    static Optional<FrontierV3ActorActuation> capture(FrontierWorldState basis,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, Mob body, AmbientActorLease scope) {
        try {
            var journey = HiveScoutPatrolProcess.journey(basis, scope.actorId());
            if (!current(basis, journey, scope)) return Optional.empty();
            return Optional.of(FrontierV3ActorActuation.capture(basis, body, journey.executionId(),
                    () -> runtime.decodedState().filter(state -> current(state, journey, scope))));
        } catch (IllegalArgumentException unavailable) { return Optional.empty(); }
    }
    static boolean current(FrontierWorldState state, ScoutPatrolJourney captured, AmbientActorLease scope) {
        if (!captured.equals(state.strategicPlans().scoutPatrols().get(scope.actorId()))) return false;
        try {
            ActorExecutionComposition.CAPABILITIES.requireAmbientMotion(state, captured.executionId(), scope);
            return true;
        } catch (IllegalArgumentException stale) { return false; }
    }
}
