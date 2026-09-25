package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/** One level-owned, durable-before-effect entry point for first field materialization. */
final class FrontierV3ResourceFieldInitialWriter {
    /** CURSOR_COMPLETE is not activation or canonical preparation confirmation. */
    enum Result { UNLOADED, FOREIGN, AMBIGUOUS, RETRY, ADVANCED, CURSOR_COMPLETE }

    private FrontierV3ResourceFieldInitialWriter() { }

    static void reserve(ServerLevel level, ResourceSite site, PhysicalIntentId intentId) {
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(Objects.requireNonNull(level));
        ledger.reserveFieldInitialization(Objects.requireNonNull(site), Objects.requireNonNull(intentId));
        ledger.persist(level);
    }

    static Result writeOne(ServerLevel level, ResourceSite site) {
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(Objects.requireNonNull(level));
        if (!(ledger.fieldClaim(Objects.requireNonNull(site).id()) instanceof FrontierV3ResourceSiteLedger.FieldInitialization claim)
                || claim.status() != FrontierV3ResourceSiteLedger.Status.PENDING || !claim.cursor().matches(site))
            throw new IllegalStateException("initial field has no exact current physical owner");
        if (claim.cursor().complete()) return Result.CURSOR_COMPLETE;
        var before = FrontierV3ResourceFieldInitialPlan.observe(level, site, claim.cursor().nextWrite());
        switch (before.disposition()) {
            case UNLOADED -> { return Result.UNLOADED; }
            case FOREIGN -> { return Result.FOREIGN; }
            case APPLIED -> { return Result.AMBIGUOUS; }
            case BEFORE -> { }
        }
        if (!claim.cursor().prepared()) {
            ledger.prepareFieldInitialization(site, before);
            // A failed fsync propagates; no block is touched before this returns.
            ledger.persist(level);
        }
        var applied = FrontierV3ResourceFieldInitialPlan.applyPrepared(level, site, ledger);
        if (!applied.writtenThisCall()) {
            return switch (applied.disposition()) {
                case UNLOADED -> Result.UNLOADED;
                case FOREIGN -> Result.FOREIGN;
                case APPLIED -> Result.AMBIGUOUS;
                case BEFORE -> Result.RETRY;
            };
        }
        ledger.advanceFieldInitialization(site, applied);
        // Only the exact write observed in this call can move the durable cursor.
        ledger.persist(level);
        return ((FrontierV3ResourceSiteLedger.FieldInitialization) ledger.fieldClaim(site.id())).cursor().complete()
                ? Result.CURSOR_COMPLETE : Result.ADVANCED;
    }

    static void activate(ServerLevel level, ResourceSite site, ResourceFieldCycle target,
                         FrontierV3ResourceFieldWitness witness) {
        FrontierV3ResourceSiteLedger ledger = FrontierV3ResourceSiteLedger.get(Objects.requireNonNull(level));
        ledger.activateField(level, site, target, witness);
        ledger.persist(level);
    }
}
