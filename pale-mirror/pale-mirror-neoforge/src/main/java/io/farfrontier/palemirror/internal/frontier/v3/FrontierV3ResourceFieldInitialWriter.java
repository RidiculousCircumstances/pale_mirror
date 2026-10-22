package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/** One level-owned, durable-before-effect entry point for first field materialization. */
final class FrontierV3ResourceFieldInitialWriter {
    /** Allocation ceiling for replay-safe presentation, not growth/work cadence. */
    static final int MAX_BATCH_WRITES = FrontierV3ManagedBlockWrites.MAX_PLANNED_WRITES;
    /** CURSOR_COMPLETE is not activation or canonical preparation confirmation. */
    enum Result { UNLOADED, FOREIGN, AMBIGUOUS, RETRY, ADVANCED, CURSOR_COMPLETE }

    private FrontierV3ResourceFieldInitialWriter() { }

    static void reserve(ServerLevel level, ResourceSite site, PhysicalIntentId intentId) {
        reserve(level, site, intentId, ResourceFieldCycle.seeded(site.id(), site.layout(), 1));
    }

    static void reserve(ServerLevel level, ResourceSite site, PhysicalIntentId intentId, ResourceFieldCycle target) {
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(Objects.requireNonNull(level));
        ledger.reserveFieldInitialization(Objects.requireNonNull(site), Objects.requireNonNull(intentId), target);
        ledger.persist(level);
    }

    static Result writeBatch(ServerLevel level, FrontierV3ServerRuntime<io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState, ?> runtime,
                             ResourceSite site) {
        if (!FrontierV3PhysicalWorld.isPhysical(level) || !FrontierV3ServerLifecycle.ownsRuntime(level, runtime))
            throw new IllegalArgumentException("initial field projection has a foreign runtime or dimension");
        return writePreparedBatch(level, site,
                writes -> FrontierV3ManagedBlockWrites.preparePlannedWrites(level, runtime, writes));
    }

    static int initialWriteBudget(int operations) {
        if (operations < 1) throw new IllegalArgumentException("initial projection needs a positive operational budget");
        return Math.min(operations, MAX_BATCH_WRITES);
    }

    /** Component seam: before-write preparation is mandatory, never an implicit permissive fallback. */
    static Result writePreparedBatch(ServerLevel level, ResourceSite site,
            java.util.function.Predicate<java.util.List<FrontierV3ManagedBlockWrites.PlannedWrite>> prepare) {
        Objects.requireNonNull(prepare, "initial field before-write preparation");
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(Objects.requireNonNull(level));
        if (!(ledger.fieldClaim(Objects.requireNonNull(site).id()) instanceof FrontierV3ResourceSiteLedger.FieldInitialization claim)
                || claim.status() != FrontierV3ResourceSiteLedger.Status.PENDING || !claim.cursor().matches(site))
            throw new IllegalStateException("initial field has no exact current physical owner");
        if (claim.cursor().complete()) return Result.CURSOR_COMPLETE;
        int limit = initialWriteBudget(io.farfrontier.palemirror.internal.world.PaleMirrorServerConfig.RUNTIME_WEIGHT_BUDGET.get());
        var budget = FrontierV3ProjectionWriteBudget.current(level, limit);
        if (!budget.available()) return Result.RETRY;
        int count = Math.min(limit, budget.remaining());
        if (!claim.cursor().prepared()) {
            var before = new java.util.ArrayList<FrontierV3ResourceFieldInitialPlan.Review>();
            for (int index = claim.cursor().nextWrite(); index < Math.min(claim.cursor().writeCount(),
                    claim.cursor().nextWrite() + count); index++) {
                var reading = FrontierV3ResourceFieldInitialPlan.observe(level, site,
                        FrontierV3ResourceFieldInitialPlan.stepAt(site, index, claim.cursor().target()));
                if (reading.disposition() == FrontierV3ResourceFieldInitialPlan.Disposition.UNLOADED) return Result.UNLOADED;
                if (reading.disposition() == FrontierV3ResourceFieldInitialPlan.Disposition.FOREIGN) return Result.FOREIGN;
                if (reading.disposition() == FrontierV3ResourceFieldInitialPlan.Disposition.APPLIED
                        && !reading.step().target().isAir()
                        && !reading.step().target().is(net.minecraft.world.level.block.Blocks.DIRT)
                        && !reading.step().target().is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) return Result.AMBIGUOUS;
                before.add(reading);
            }
            ledger.prepareFieldInitializationBatch(site, before);
            // A failed fsync propagates; no block is touched before this returns.
            ledger.persist(level);
        }
        // Includes restored prepared ranges: a failed cross-owner fence must be retried
        // before any effect, even when the field's own predecessor journal survived.
        var cursor = ((FrontierV3ResourceSiteLedger.FieldInitialization) ledger.fieldClaim(site.id())).cursor();
        var writes = new java.util.ArrayList<FrontierV3ManagedBlockWrites.PlannedWrite>();
        for (int index = cursor.nextWrite(); index < cursor.batch().orElseThrow().end(); index++) {
            var step = FrontierV3ResourceFieldInitialPlan.stepAt(site, index, cursor.target());
            writes.add(new FrontierV3ManagedBlockWrites.PlannedWrite(
                    new net.minecraft.core.BlockPos(step.position().x(), step.position().y(), step.position().z()), step.target()));
        }
        if (!prepare.test(java.util.List.copyOf(writes))) return Result.RETRY;
        Result result = Result.RETRY;
        try {
            for (int written = 0; written < count && budget.take(); written++) {
                var applied = FrontierV3ResourceFieldInitialPlan.applyPrepared(level, site, ledger);
                if (!applied.writtenThisCall() && !applied.reconciledProjection()) {
                    return switch (applied.disposition()) {
                        case UNLOADED -> Result.UNLOADED;
                        case FOREIGN -> Result.FOREIGN;
                        case APPLIED -> Result.AMBIGUOUS;
                        case BEFORE -> Result.RETRY;
                    };
                }
                ledger.advanceFieldInitialization(site, applied);
                var after = ((FrontierV3ResourceSiteLedger.FieldInitialization) ledger.fieldClaim(site.id())).cursor();
                result = after.complete() ? Result.CURSOR_COMPLETE : Result.ADVANCED;
                if (after.complete() || !after.prepared()) break;
            }
            return result;
        } finally {
            // The prepared range survives partial application; publish observed progress
            // once, never pretend that SavedData and region files saved atomically.
            ledger.persist(level);
        }
    }

    static void activate(ServerLevel level, ResourceSite site, ResourceFieldCycle target,
                         FrontierV3ResourceFieldWitness witness) {
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(Objects.requireNonNull(level));
        ledger.activateField(level, site, target, witness);
        ledger.persist(level);
    }
}
