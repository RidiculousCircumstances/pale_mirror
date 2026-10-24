package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Fixed-store recovery consumes native saved inventory evidence, independently of player visits. */
final class FrontierV3ReferenceContainerStoredRecovery {
    private static final Map<ServerLevel, Session> SESSIONS = new WeakHashMap<>();
    private FrontierV3ReferenceContainerStoredRecovery() { }
    static void offer(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, PhysicalCustodyLease lease) {
        var surface = state.inventory().surfaces().get(lease.objectId());
        if (surface == null || !(surface.location() instanceof ContainerLocation.Fixed fixed)
                || lease.status() == PhysicalCustodyLeaseStatus.UNRESOLVED
                || FrontierV3ContainerEffectFence.pending(level, state, lease.objectId())) return;
        var session = session(level, runtime);
        if (session.pending != null) return;
        var column = new ChunkPos(Math.floorDiv(fixed.position().x(), 16), Math.floorDiv(fixed.position().z(), 16));
        if (!FrontierV3StoredTerrainInspection.unloaded(level, column)) return;
        var attempt = new Attempt(lease, FrontierV3StoredTerrainInspection.epoch(level));
        if (attempt.equals(session.attempted.get(lease.scopeId()))) return;
        session.attempted.put(lease.scopeId(), attempt);
        session.pending = new Pending(lease, FrontierV3StoredTerrainInspection.inspect(level, column));
    }
    static void progress(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        var session = session(level, runtime); var pending = session.pending;
        if (pending == null || !pending.future().isDone()) return;
        session.pending = null;
        var state = runtime.decodedState().orElse(null);
        if (state == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        session.attempted.keySet().retainAll(state.replicaCustody().custodyByScope().keySet());
        var lease = pending.lease();
        if (!lease.equals(state.replicaCustody().custodyByScope().get(lease.scopeId()))
                || FrontierV3ContainerEffectFence.pending(level, state, lease.objectId())) return;
        try {
            var snapshot = pending.future().join();
            if (!snapshot.current(level)) return;
            var actual = FrontierV3StoredContainerObservation.read(state, lease.objectId(),
                    snapshot.data().orElse(null), level.registryAccess());
            if (actual.isPresent() && FrontierV3ReferenceContainerCustodyExecutor.confirmSavedFixedProjection(
                    level, runtime, state, lease, actual.orElseThrow()))
                PaleMirrorMod.LOGGER.info("PMV3_CONTAINER_STORED_RECOVERY container={} epoch={} result=SAVED_IMAGE_CONFIRMED",
                        lease.objectId().value(), lease.authorityEpoch());
            else PaleMirrorMod.LOGGER.warn("PMV3_CONTAINER_STORED_RECOVERY container={} epoch={} reason={}",
                    lease.objectId().value(), lease.authorityEpoch(), actual.isEmpty() ? "MISSING_OR_FOREIGN_SAVED_CONTAINER" : "SAVED_IMAGE_DIFFERS");
        } catch (CompletionException | IllegalArgumentException malformed) {
            PaleMirrorMod.LOGGER.warn("PMV3_CONTAINER_STORED_RECOVERY container={} reason={}", lease.objectId().value(), malformed.toString());
        }
    }
    private static Session session(ServerLevel level, FrontierV3ServerRuntime<?, ?> runtime) {
        var current = SESSIONS.get(level);
        if (current == null || current.runtime != runtime) { current = new Session(runtime); SESSIONS.put(level, current); }
        return current;
    }
    private record Attempt(PhysicalCustodyLease lease, long writeEpoch) { }
    private record Pending(PhysicalCustodyLease lease, CompletableFuture<FrontierV3StoredTerrainInspection.Snapshot> future) { }
    private static final class Session {
        final FrontierV3ServerRuntime<?, ?> runtime;
        final Map<SubjectId, Attempt> attempted = new HashMap<>();
        Pending pending;
        Session(FrontierV3ServerRuntime<?, ?> runtime) { this.runtime = runtime; }
    }
}
