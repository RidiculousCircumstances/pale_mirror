package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Composition of owners observing the ONE native entity-storage boundary. Business
 * interpretation stays in each observer; none of these methods starts a second save. */
final class FrontierV3EntityPersistenceObservers {
    private FrontierV3EntityPersistenceObservers() { }
    private static boolean active(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime) {
        return FrontierV3PhysicalWorld.isPhysical(level) && runtime != null && runtime.status().kind() == FrontierV3RuntimeStatus.Kind.ACTIVE;
    }
    static CompletableFuture<?> writePrerequisite(ServerLevel level) {
        if (!FrontierV3PhysicalWorld.isPhysical(level)) return CompletableFuture.completedFuture(null);
        // Captured on the native writer's thread. An entity image cannot reach
        // disk before its residence/history, including immediately after joining.
        return FrontierV3AmbientCarrierLedger.get(level, FrontierV3PhysicalWorld.WORLD_ID)
                .persistAsync(level, FrontierV3PhysicalWorld.WORLD_ID);
    }
    static void write(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            ChunkPos chunk, CompoundTag data, CompletableFuture<Void> written) {
        if (!active(level, runtime)) return;
        FrontierV3ActorAdoptionPersistence.observeWrite(level, runtime, chunk, data, written);
        FrontierV3SceneDeparturePersistence.observeWrite(level, runtime, chunk, data, written);
        FrontierV3OperationEntitySaves.observe(level, chunk, data, written);
    }
    static void complete(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            boolean complete, Supplier<CompletableFuture<Void>> synchronize) {
        if (!active(level, runtime)) return;
        var state = runtime.decodedState().orElse(null); if (state == null) return;
        var world = state.bootstrap().worldId();
        // Raw observations are published once per native pass. Neither this
        // asynchronous submission nor the native callback grants COLD custody.
        // Exact saved-departure publication still fences release/reconstruction.
        try { FrontierV3AmbientCarrierLedger.get(level, world).persistAsync(level, world); }
        catch (RuntimeException failure) {
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.error("Scene raw departure publication failed; custody remains unresolved", failure);
        }
        FrontierV3ActorAdoptionPersistence.completeSavePass(level, runtime, complete, synchronize);
        FrontierV3SceneDeparturePersistence.completeSavePass(level, runtime, complete, synchronize);
        FrontierV3OperationEntitySaves.complete(level, complete, synchronize);
    }
    static void departed(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Supplier<CompletableFuture<Void>> synchronize) {
        // Positive exact-incarnation writes, not a global absence proof. Native
        // pending loads outside this completed unload batch grant no evidence.
        complete(level, runtime, true, synchronize);
    }
}
