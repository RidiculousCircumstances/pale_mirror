package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSiteHarvestSceneSupport;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import net.minecraft.server.level.ServerLevel;

/** Harvest-owned physical effects must close before hand custody can leave its scene. */
final class FrontierV3HarvestSceneReleaseBarrier {
    private FrontierV3HarvestSceneReleaseBarrier() { }

    static boolean ready(ServerLevel level, FrontierWorldState state, SceneLease lease) {
        return ready(FrontierV3ResourceSiteLedger.get(level), state, lease);
    }

    static boolean ready(FrontierV3ResourceSiteLedger ledger, FrontierWorldState state, SceneLease lease) {
        var cause = FrontierSceneBehaviors.resourceSiteHarvest(lease);
        // Saved body evidence proves residence, not completion of the prepared cell.
        // The same barrier protects ordinary release and both stored-recovery paths.
        // Keep the original scope available for loaded-world effect reconciliation;
        // do not enter DRAINING and mistake the owner's lawful refusal for lost custody.
        if (!FrontierResourceSiteHarvestSceneSupport.isTerminalReceiptRelease(state, cause)
                && FrontierResourceSiteHarvestSceneSupport.require(state, cause).progress().hasPendingCrop()) return false;
        return ready(ledger, lease);
    }

    static boolean ready(FrontierV3ResourceSiteLedger ledger, SceneLease lease) {
        var cause = FrontierSceneBehaviors.resourceSiteHarvest(lease);
        var delivery = ledger.fieldDelivery(cause.siteId());
        // Another worker on the same field owns a different effect. Never serialize
        // all farmers behind a site-wide test, nor drop an unresolved stale witness.
        return delivery == null || !delivery.leaseId().equals(lease.id());
    }
}
