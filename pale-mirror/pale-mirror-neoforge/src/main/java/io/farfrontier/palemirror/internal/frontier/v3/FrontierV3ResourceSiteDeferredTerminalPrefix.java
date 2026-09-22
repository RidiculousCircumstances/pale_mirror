package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestLineage;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgress;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteLifecycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSitePhase;
import net.minecraft.server.level.ServerLevel;

/** Exact ownership boundary between a retained HOT prefix and its COLD terminal receipt. */
final class FrontierV3ResourceSiteDeferredTerminalPrefix {
    private FrontierV3ResourceSiteDeferredTerminalPrefix() { }

    static boolean matches(ServerLevel level, FrontierWorldState state, ResourceSite site,
                           int desiredStage, int completedCropSlots, FrontierV3ResourceSiteLedger.Claim claim) {
        if (claim == null || !FrontierV3ResourceSiteExecutor.matchesHarvestProgress(level, site, claim.harvestedCropSlots())) return false;
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        ResourceSiteHarvestLineage lineage = lifecycle.harvestLineage().filter(ResourceSiteHarvestLineage::receiptPending)
                .filter(candidate -> !candidate.composedIntoCanonicalSuccessor(state)).orElse(null);
        PhysicalIntent intent = lineage == null ? null : state.physicalIntents().get(lineage.predecessorIntentId());
        return ownsExactPrefix(site, lineage, claim, intent)
                && admits(lifecycle, claim, intent.status(), desiredStage, completedCropSlots);
    }

    static boolean awaitingProjection(ServerLevel level, FrontierWorldState state, ResourceSite site, PhysicalIntent intent) {
        if (intent == null) return false;
        FrontierV3ResourceSiteLedger.Claim claim = FrontierV3ResourceSiteLedger.get(level).claim(site.id());
        ResourceSiteLifecycle lifecycle = state.resourceSites().site(site.id());
        return matches(level, state, site, lifecycle.growthStage(), 0, claim)
                && lifecycle.harvestLineage().map(ResourceSiteHarvestLineage::predecessorIntentId)
                .filter(intent.id()::equals).isPresent();
    }

    static boolean admits(ResourceSiteLifecycle lifecycle, FrontierV3ResourceSiteLedger.Claim claim,
                          PhysicalIntentStatus intentStatus, int desiredStage, int completedCropSlots) {
        return lifecycle != null && claim != null && claim.status() == FrontierV3ResourceSiteLedger.Status.ACTIVE
                && claim.stage() == ResourceSiteLifecycle.MATURE_STAGE && claim.harvestedCropSlots() > 0
                && claim.harvestedCropSlots() < ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS
                && intentStatus == PhysicalIntentStatus.RUNNING && lifecycle.phase() == ResourceSitePhase.GROWING
                && desiredStage == lifecycle.growthStage() && completedCropSlots == 0
                && lifecycle.harvestLineage().filter(ResourceSiteHarvestLineage::receiptPending).isPresent();
    }

    static boolean ownsExactPrefix(ResourceSite site, ResourceSiteHarvestLineage lineage,
                                   FrontierV3ResourceSiteLedger.Claim claim, PhysicalIntent intent) {
        // Status alone is insufficient: a foreign RUNNING intent, look-alike site, or stale
        // claim remains a local conflict. This owner alone may finish its terminal AIR suffix.
        return lineage != null && intent != null && intent.id().equals(lineage.predecessorIntentId())
                // The durable facility claim is intentionally stable across harvest jobs.
                // It is the plan-derived site-projection identity, or (for the bounded
                // hand-off itself) the exact predecessor intent.  Requiring only the latter
                // misclassifies a lawful COLD terminal prefix as foreign on player re-entry.
                && FrontierV3ResourceSiteExecutor.ownsFacilityClaim(claim.intentId(), site, intent.id())
                && intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST
                && intent.causeSubjectId().equals(site.id())
                && intent.roles().require(PhysicalIntentSubjectRole.SITE).equals(site.id())
                && intent.roles().require(PhysicalIntentSubjectRole.RESOURCE_SITE_JOB).equals(lineage.predecessorJobId());
    }
}
