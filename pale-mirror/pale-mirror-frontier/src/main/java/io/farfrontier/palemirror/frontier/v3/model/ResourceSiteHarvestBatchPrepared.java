package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable capacity reservation before a full HOT hand is written into the depot. */
public record ResourceSiteHarvestBatchPrepared(SubjectId siteId, SubjectId jobId, SceneLeaseId leaseId,
                                               int deliveredYieldBefore,
                                               InventoryCustody.ContainerSlot nextOutputSlot) implements FrontierPayload {
    public ResourceSiteHarvestBatchPrepared {
        Objects.requireNonNull(siteId, "field batch site"); Objects.requireNonNull(jobId, "field batch job");
        Objects.requireNonNull(leaseId, "field batch scene"); Objects.requireNonNull(nextOutputSlot, "field batch next slot");
        if (!siteId.value().startsWith("site:") || !jobId.value().startsWith("job:site-harvest-")
                || deliveredYieldBefore < 0 || deliveredYieldBefore % 64 != 0)
            throw new IllegalArgumentException("field batch preparation has invalid declared identities");
    }

    @Override public String type() { return "frontier.resource_site_harvest_batch_prepared"; }
}
