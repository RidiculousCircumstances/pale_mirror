package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionRecord;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.DeferredAftermath;
import io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathCell;
import io.farfrontier.palemirror.frontier.v3.model.DeferredAftermathCellStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierV3FixtureCatalog;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial;
import io.farfrontier.palemirror.frontier.v3.persistence.AppendReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.CompactionReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierStore;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotReceipt;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Narrow component evidence for the aftermath owners; the real-level GameTest owns registry-seam evidence. */
class FrontierV3AftermathOwnerSeamTest {
    @Test
    void ordinaryColdDueActionWaitsForItsRealBoundedProjectorBeforeDeferredAftermathResolves() {
        var base = FrontierV3FixtureCatalog.coldBomberAftermathConfiguration(new WorldId("frontier:aftermath-owner-seam"), 41L);
        assertTrue(base.initialState().deferredAftermath().entries().isEmpty(), "the scheduled fixture begins with no aftermath history");
        assertTrue(base.initialState().physicalDeltas().isEmpty(), "the scheduled fixture begins with no semantic-loss mask");
        var engine = (io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalStateAccess<FrontierWorldState, FrontierWorldProjection>) FrontierEngines.createCanonicalStateAccess(base);
        long dueAt = base.initialSchedules().getFirst().dueAt().ticks();
        engine.advanceTo(new SimInstant(dueAt), new WorkBudget(64, 512));
        FrontierWorldState cold = engine.canonicalState().state();
        DeferredAftermath aftermath = cold.deferredAftermath().entries().values().stream().findFirst().orElseThrow();
        DeferredAftermathCell target = aftermath.cells().getFirst();
        assertTrue(cold.physicalDeltas().containsKey(target.position()), "ordinary COLD scheduling commits the loss before either physical owner runs");
        var structuralPlan = io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan.compileStructuralBaseline(cold);
        var baselineTarget = structuralPlan.cells().get(target.position());
        assertTrue(baselineTarget != null && FrontierV3GrayboxExecutor.matchesKnownLoss(cold.physicalDeltas().get(target.position()), baselineTarget),
                "the ordinary loss must mask the exact projector cell rather than a test-only coordinate");

        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(rebased(base, cold, dueAt), new EphemeralStore(), 10_000);
        try {
            FrontierWorldState runtimeCold = runtime.decodedState().orElseThrow();
            assertTrue(runtimeCold.physicalDeltas().containsKey(target.position()), "the runtime must retain the ordinary COLD loss");
            assertTrue(FrontierV3GrayboxExecutor.matchesKnownLoss(runtimeCold.physicalDeltas().get(target.position()), baselineTarget), "runtime hydration must retain the exact loss identity");
            TestPhysicalWorld world = new TestPhysicalWorld(cold, target.position());
            assertNull(world.claim(target.position()), "the physical ledger starts untouched");
            FrontierV3AftermathOwnerComposition.tick(world, runtime);
            assertEquals(DeferredAftermathCellStatus.PENDING, cell(runtime, aftermath).status(),
                    "AIR remains pending while the real bounded projector has not reached this exact target");
            assertNull(world.claim(target.position()), "aftermath cannot manufacture the projector's tombstone");
            FrontierWorldState restartState = new FrontierWorldStateCodec().decode(runtime.checkpointImage().orElseThrow().canonicalState());
            FrontierV3GrayboxExecutor.forget(runtime); runtime.shutdown();
            runtime = FrontierV3ServerRuntime.start(rebased(base, restartState, dueAt), new EphemeralStore(), 10_000);
            assertEquals(DeferredAftermathCellStatus.PENDING, cell(runtime, aftermath).status(),
                    "codec restart before projector classification retains absence as pending rather than a foreign conflict");

            // The normal projector probes at most two natural chunks per turn rather than
            // scanning every declared chunk. Known natural chunks are retained in a bounded
            // fair queue, so this exact deferred owner cannot wait for a world-plan lap.
            for (int tick = 0; tick < 512 && cell(runtime, aftermath).status() != DeferredAftermathCellStatus.REALIZED; tick++) {
                FrontierV3AftermathOwnerComposition.tick(world, runtime);
            }
            assertEquals(DeferredAftermathCellStatus.REALIZED, cell(runtime, aftermath).status(),
                    "projector provenance and deferred aftermath converge exactly once");
            assertTrue(world.isAir(target.position()), "the COLD loss stays AIR; no old physical effect is replayed");
            assertTrue(world.claim(target.position()).conflicted(), "the exact tombstone remains the durable anti-replay fence");
            long revision = runtime.canonicalState().orElseThrow().revision().value();
            FrontierV3AftermathOwnerComposition.tick(world, runtime);
            assertEquals(revision, runtime.canonicalState().orElseThrow().revision().value(), "terminal aftermath cannot resolve twice");
        } finally { FrontierV3GrayboxExecutor.forget(runtime); runtime.shutdown(); }
    }

    @Test
    void wrongScheduledCombatIdentityCannotManufactureTheColdAftermath() {
        var base = FrontierV3FixtureCatalog.coldBomberAftermathConfiguration(new WorldId("frontier:aftermath-owner-stale-action"), 41L);
        ScheduledAction due = base.initialSchedules().getFirst();
        ScheduledAction wrong = new ScheduledAction(new ScheduleId("schedule:settlement-assault-combat-wrong-identity"), due.dueAt(), due.priority(), due.subject(), due.kind(), due.weight());
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), base.initialState(), base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), List.of(wrong),
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var engine = (io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalStateAccess<FrontierWorldState, FrontierWorldProjection>) FrontierEngines.createCanonicalStateAccess(configuration);

        engine.advanceTo(due.dueAt(), new WorkBudget(64, 512));

        assertTrue(engine.canonicalState().state().deferredAftermath().entries().isEmpty(),
                "a stale/wrong scheduled identity cannot create the exact COLD bomber cause");
        assertTrue(engine.canonicalState().state().physicalDeltas().isEmpty(),
                "a rejected scheduled action cannot manufacture the semantic loss either");
    }

    @Test
    void positiveForeignMaterialSurvivesAndBecomesOneLocalConflict() {
        var base = FrontierV3FixtureCatalog.coldBomberAftermathConfiguration(new WorldId("frontier:aftermath-owner-foreign"), 41L);
        var engine = (io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalStateAccess<FrontierWorldState, FrontierWorldProjection>) FrontierEngines.createCanonicalStateAccess(base);
        long dueAt = base.initialSchedules().getFirst().dueAt().ticks(); engine.advanceTo(new SimInstant(dueAt), new WorkBudget(64, 512));
        FrontierWorldState cold = engine.canonicalState().state(); DeferredAftermath aftermath = cold.deferredAftermath().entries().values().stream().findFirst().orElseThrow();
        DeferredAftermathCell target = aftermath.cells().getFirst(); TestPhysicalWorld world = new TestPhysicalWorld(cold, target.position());
        world.foreign(target.position(), GrayboxMaterial.HIVE_GANGLION);
        FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime = FrontierV3ServerRuntime.start(rebased(base, cold, dueAt), new EphemeralStore(), 10_000);
        try {
            FrontierV3AftermathOwnerComposition.tick(world, runtime);
            assertEquals(DeferredAftermathCellStatus.CONFLICTED, cell(runtime, aftermath).status(), "positive unowned material is one local typed conflict");
            assertTrue(world.hasMaterial(target.position(), GrayboxMaterial.HIVE_GANGLION), "foreign material is preserved, never overwritten");
            long revision = runtime.canonicalState().orElseThrow().revision().value(); FrontierV3AftermathOwnerComposition.tick(world, runtime);
            assertEquals(revision, runtime.canonicalState().orElseThrow().revision().value(), "a terminal foreign conflict cannot replay");
        } finally { FrontierV3GrayboxExecutor.forget(runtime); runtime.shutdown(); }
    }

    private static DeferredAftermathCell cell(FrontierV3ServerRuntime<FrontierWorldState, FrontierWorldProjection> runtime, DeferredAftermath original) {
        return runtime.decodedState().orElseThrow().deferredAftermath().entries().get(original.id()).cells().getFirst();
    }
    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> rebased(FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base,
                                                                                                      FrontierWorldState state, long instant) {
        return new FrontierEngineConfiguration<>(base.worldId(), state, new SimInstant(instant), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                base.stateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
    }
    private static final class TestPhysicalWorld implements FrontierV3AftermathPhysicalWorld {
        private final Set<Long> loadedChunks; private final FrontierV3GrayboxLedger ledger = FrontierV3GrayboxLedger.inMemory();
        private final Map<BlockPosition, GrayboxMaterial> blocks = new HashMap<>();
        private TestPhysicalWorld(FrontierWorldState state, BlockPosition target) {
            List<Long> chunks = io.farfrontier.palemirror.frontier.v3.model.FrontierGrayboxPlan.compileStructuralBaseline(state).cells().values().stream()
                    .map(cell -> chunk(cell.position())).distinct().sorted().toList();
            int targetChunk = chunks.indexOf(chunk(target));
            if (targetChunk < 64) throw new IllegalStateException("fixture cannot delay its target behind one bounded projector turn");
            java.util.Set<Long> loaded = new java.util.HashSet<>(chunks.subList(0, 64));
            loaded.add(chunk(target)); loadedChunks = Set.copyOf(loaded);
        }
        @Override public boolean naturallyLoaded(BlockPosition position) {
            return loadedChunks.contains(chunk(position));
        }
        @Override public boolean isAir(BlockPosition position) { return !blocks.containsKey(position); }
        @Override public boolean hasMaterial(BlockPosition position, GrayboxMaterial material) { return blocks.get(position) == material; }
        @Override public boolean placeMaterial(BlockPosition position, GrayboxMaterial material) { blocks.put(position, material); return true; }
        @Override public boolean clear(BlockPosition position) { blocks.remove(position); return true; }
        @Override public FrontierV3GrayboxLedger ledger() { return ledger; }
        private void foreign(BlockPosition position, GrayboxMaterial material) { blocks.put(position, material); }
        private FrontierV3GrayboxLedger.Claim claim(BlockPosition position) { return ledger.claim(new net.minecraft.core.BlockPos(position.x(), position.y(), position.z())); }
        private static long chunk(BlockPosition position) { return ((long) (position.x() >> 4) << 32) ^ (position.z() >> 4 & 0xffffffffL); }
    }
    private static final class EphemeralStore implements FrontierStore {
        @Override public RecoveryImage recover(WorldId worldId) { return new RecoveryImage(worldId, Optional.empty(), List.of()); }
        @Override public AppendReceipt append(TransactionRecord transaction, Durability durability) { return new AppendReceipt(transaction.id(), transaction.revision(), durability, transaction.revision().value()); }
        @Override public SnapshotReceipt installSnapshot(SnapshotRecord snapshot) { throw new UnsupportedOperationException("owner seam does not compact"); }
        @Override public CompactionReceipt compact(WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision coveredRevision) { throw new UnsupportedOperationException("owner seam does not compact"); }
    }
}
