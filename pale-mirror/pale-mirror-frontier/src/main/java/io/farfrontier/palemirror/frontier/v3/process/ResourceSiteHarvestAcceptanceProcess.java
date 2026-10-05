package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** Field-owned acceptance lifecycle, independent of scene, movement and current crop selection. */
public final class ResourceSiteHarvestAcceptanceProcess {
    private ResourceSiteHarvestAcceptanceProcess() { }
    public static FrontierWorldState acknowledge(FrontierWorldState state, SubjectId owner,
                                                  ResourceSiteHarvestWorkAcknowledged acknowledged) {
        var accepted = acknowledged.acceptance();
        var receipt = accepted.receipt();
        if (!owner.equals(receipt.siteId()))
            throw new IllegalArgumentException("field acknowledgement has a foreign declared site");
        var site = state.resourceSites().site(owner);
        var job = site.harvestJob(receipt.jobId()).orElseThrow(
                () -> new IllegalArgumentException("field acknowledgement lost its exact retained job"));
        var updated = site.acknowledgeHarvestWork(job, accepted);
        return state.withChanges(FrontierWorldStateUpdate.begin().resourceSites(
                state.resourceSites().replace(updated, state.resourceSites().cycle(owner))));
    }
}
