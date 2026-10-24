package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;

/** Recovery from the field owner's retired physical receipt, never from matching counts alone. */
final class FrontierV3HarvestRetainedHandEvidence {
    private FrontierV3HarvestRetainedHandEvidence() { }

    static boolean witnessed(ServerLevel level, FrontierWorldState state, ResourceSiteHarvestJob job,
                             FungiblePhysicalObservation.Stack observed) {
        if (job.hasPendingPhysicalWork() || state.resourceSites().harvestMutationPending(job)) return false;
        var cycle = state.resourceSites().cycle(job.siteId());
        var claim = FrontierV3ResourceSiteLedger.get(level).fieldClaim(job.siteId());
        if (!(claim instanceof FrontierV3ResourceSiteLedger.FieldOwnership owner)
                || owner.status() != FrontierV3ResourceSiteLedger.Status.ACTIVE || !owner.witness().matchesCycle(cycle)) return false;
        for (var cell : cycle.layout().cells()) {
            var retained = owner.witness().cell(cell.id());
            if (retained.pending().isPresent()) continue;
            var seal = retained.retiredWork().orElse(null);
            if (seal == null || !matches(job, cycle, seal.receipt(), observed)) continue;
            var hand = seal.receipt().observedHand().orElseThrow();
            // The old physical scope positively closed after acknowledging this exact
            // effect. A new/current scope or another worker's history cannot supply proof.
            return state.sceneLeases().values().stream().anyMatch(previous ->
                    previous.status() == SceneLeaseStatus.CLOSED && previous.revision() == hand.authorityEpoch()
                    && previous.cause().equals(new ResourceSiteHarvestSceneCause(job.siteId(), job.id()))
                    && previous.members().size() == 1
                    && previous.members().getFirst().actorId().equals(job.workerId())
                    && previous.members().getFirst().entityId().equals(hand.address().entityId()));
        }
        return false;
    }

    static boolean matches(ResourceSiteHarvestJob job, ResourceFieldCycle cycle,
                           ResourceSiteHarvestProgressed receipt, FungiblePhysicalObservation.Stack observed) {
        return !job.hasPendingPhysicalWork() && receipt.jobId().equals(job.id())
                && receipt.siteId().equals(job.siteId()) && receipt.epoch() == cycle.epoch()
                && receipt.layoutRevision() == cycle.layout().revision()
                && receipt.completedCropSlots() == job.progress().completedCropSlots()
                && observed.itemKind().equals("minecraft:wheat")
                && observed.quantity() == job.undeliveredYieldQuantity()
                && receipt.observedHand().filter(hand -> hand.quantity() > 0
                    && hand.quantity() == observed.quantity() && hand.address().equals(observed.address())
                    && hand.address().actorId().equals(job.workerId())).isPresent();
    }
}
