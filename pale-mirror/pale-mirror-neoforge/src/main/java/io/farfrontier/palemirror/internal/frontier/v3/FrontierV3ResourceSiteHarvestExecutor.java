package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.Optional;

/** Read-only current cell-work diagnostic. Physical work belongs to scene/cell/delivery owners. */
final class FrontierV3ResourceSiteHarvestExecutor {
    record Readiness(boolean fieldLoaded, boolean ownedField, boolean conflicted, boolean pendingCellEffect,
                     String workPhase, String workerId, int carriedQuantity, int deliveredQuantity) { }
    private FrontierV3ResourceSiteHarvestExecutor() { }
    static Optional<Readiness> readiness(ServerLevel level, FrontierWorldState state, PhysicalIntentId intentId) {
        var job = state.resourceSites().sites().values().stream().flatMap(site -> site.harvestJobs().values().stream())
                .filter(value -> value.intentId().equals(intentId)).findFirst().orElse(null);
        if (job == null) return Optional.empty();
        var site = state.resourceSite(job.siteId()); var cycle = state.resourceSites().cycle(job.siteId());
        var claim = FrontierV3ResourceSiteLedger.get(level).fieldClaim(job.siteId());
        boolean loaded = FrontierV3ResourceSiteExecutor.loaded(level, site);
        boolean owned = claim instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner && owner.witness().matchesCycle(cycle);
        boolean blocked = claim != null && claim.status() == FrontierV3ResourceSiteLedger.Status.CONFLICT;
        boolean pending = claim instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner
                && owner.witness().journalBuckets().values().stream().flatMap(cells -> cells.values().stream()).anyMatch(cell -> cell.pending().isPresent());
        return Optional.of(new Readiness(loaded,owned,blocked,pending,
                job.returningForBatch() ? "DELIVERING" : job.progress().complete() ? "WORK_COMPLETE" : "CELL_WORK",job.workerId().value(),job.undeliveredYieldQuantity(),job.deliveredYieldQuantity()));
    }
}
