package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import net.minecraft.server.level.ServerLevel;
import java.util.function.Supplier;

/** Explicit durable fixture setup for tests of subsequent body behavior.
 * Production never uses this blocking helper. Async admission itself is tested
 * separately with the real receipt and GameTest's event-driven sequence.
 */
final class FrontierV3BodyAdmissionGameTestFixture {
    private FrontierV3BodyAdmissionGameTestFixture() { }
    static FrontierV3AmbientActorExecutor.Result ambient(ServerLevel level, FrontierWorldState state,
            SubjectId actor, BodyPosition position) {
        return ambient(level, state.bootstrap().worldId(),
                () -> FrontierV3AmbientActorExecutor.materialize(level, state, actor, position));
    }
    static FrontierV3AmbientActorExecutor.Result ambient(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state,
            SubjectId actor, BodyPosition position) {
        return ambient(level, state.bootstrap().worldId(),
                () -> FrontierV3AmbientActorExecutor.materialize(level, runtime, state, actor, position));
    }
    static FrontierV3ActorBodyController.Result body(ServerLevel level, FrontierWorldState state,
            FrontierV3ActorBodyController.BirthRequest request) {
        return body(level, state.bootstrap().worldId(),
                () -> FrontierV3ActorBodyController.materialize(level, state, request));
    }
    static FrontierV3AmbientActorExecutor.Result ambient(ServerLevel level, WorldId world,
            Supplier<FrontierV3AmbientActorExecutor.Result> attempt) {
        var result = attempt.get();
        if (result != FrontierV3AmbientActorExecutor.Result.DEFERRED) return result;
        FrontierV3AmbientCarrierLedger.get(level, world).persist(level, world);
        return attempt.get();
    }
    static FrontierV3ActorBodyController.Result body(ServerLevel level, WorldId world,
            Supplier<FrontierV3ActorBodyController.Result> attempt) {
        var result = attempt.get();
        if (result != FrontierV3ActorBodyController.Result.DEFERRED) return result;
        FrontierV3AmbientCarrierLedger.get(level, world).persist(level, world);
        return attempt.get();
    }
}
