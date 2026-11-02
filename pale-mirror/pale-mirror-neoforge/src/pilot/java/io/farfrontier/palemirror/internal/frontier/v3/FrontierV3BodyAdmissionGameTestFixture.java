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
    static void ambientWhenReady(net.minecraft.gametest.framework.GameTestHelper helper,
            Supplier<FrontierV3AmbientActorExecutor.Result> attempt,
            java.util.function.Consumer<FrontierV3AmbientActorExecutor.Result> complete) {
        awaitAmbient(helper,attempt,complete,0L);
    }
    private static void awaitAmbient(net.minecraft.gametest.framework.GameTestHelper helper,
            Supplier<FrontierV3AmbientActorExecutor.Result> attempt,
            java.util.function.Consumer<FrontierV3AmbientActorExecutor.Result> complete,long deadline) {
        helper.runAfterDelay(1,()->{
            long until=deadline==0L?System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10):deadline;
            var result=attempt.get();
            if(result!=FrontierV3AmbientActorExecutor.Result.DEFERRED && result!=FrontierV3AmbientActorExecutor.Result.PENDING){complete.accept(result);return;}
            helper.assertTrue(System.nanoTime()<until,"ambient native admission made no progress: "+result);
            awaitAmbient(helper,attempt,complete,until);
        });
    }
    static void observeWhenReady(net.minecraft.gametest.framework.GameTestHelper helper,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, net.minecraft.world.entity.Mob body,
            Runnable complete) {
        awaitObservation(helper,runtime,body,complete,0L);
    }
    private static void awaitObservation(net.minecraft.gametest.framework.GameTestHelper helper,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, net.minecraft.world.entity.Mob body,
            Runnable complete,long deadline) {
        helper.runAfterDelay(1,()->{
            long until=deadline==0L?System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10):deadline;
            FrontierV3ActorBodyController.confirmPresent(helper.getLevel(),runtime,body);
            var state=runtime.decodedState().orElseThrow();
            var declaration=FrontierV3ActorCarrierComposition.declaredBy(body).orElseThrow();
            var id=io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state,declaration.actorId());
            if(FrontierV3ActorBodyController.readyForExecution(helper.getLevel(),state,java.util.List.of(id))){complete.run();return;}
            helper.assertTrue(System.nanoTime()<until,"indexed native body observation made no progress: "+declaration.actorId());
            awaitObservation(helper,runtime,body,complete,until);
        });
    }
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
        FrontierV3OperationLedger.get(level).persist(level);
        var admitted = attempt.get();
        FrontierV3OperationLedger.get(level).persist(level);
        return admitted;
    }
    static FrontierV3ActorBodyController.Result body(ServerLevel level, WorldId world,
            Supplier<FrontierV3ActorBodyController.Result> attempt) {
        var result = attempt.get();
        if (result != FrontierV3ActorBodyController.Result.DEFERRED) return result;
        FrontierV3AmbientCarrierLedger.get(level, world).persist(level, world);
        FrontierV3OperationLedger.get(level).persist(level);
        var admitted = attempt.get();
        FrontierV3OperationLedger.get(level).persist(level);
        return admitted;
    }
}
