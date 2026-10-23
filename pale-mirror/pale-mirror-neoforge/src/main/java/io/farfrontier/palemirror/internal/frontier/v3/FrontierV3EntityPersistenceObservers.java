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
        FrontierV3ActorAdoptionPersistence.completeSavePass(level, runtime, complete, synchronize);
        FrontierV3SceneDeparturePersistence.completeSavePass(level, runtime, complete, synchronize);
        FrontierV3OperationEntitySaves.complete(level, complete, synchronize);
    }
    static void departed(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            Supplier<CompletableFuture<Void>> synchronize) {
        if (!active(level, runtime)) return;
        var state = runtime.decodedState().orElse(null); if (state == null) return;
        var world = state.bootstrap().worldId();
        // Raw departure is only an observation. Saved-departure publication and
        // the body's own durable checkpoint still fence release/reconstruction.
        // Queue this prefix in the same journal instead of stalling every native
        // chunk departure (including passes with no new PM actor observation).
        try { FrontierV3AmbientCarrierLedger.get(level, world).persistAsync(level, world); }
        catch (RuntimeException failure) {
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.error("Scene raw departure publication failed; custody remains unresolved", failure);
        }
        // These are positive exact-incarnation writes, not global absence proofs.
        FrontierV3ActorAdoptionPersistence.completeSavePass(level, runtime, true, synchronize);
        FrontierV3SceneDeparturePersistence.confirmRecordedDepartures(level, runtime, synchronize);
        FrontierV3OperationEntitySaves.complete(level, true, synchronize);
    }
}
