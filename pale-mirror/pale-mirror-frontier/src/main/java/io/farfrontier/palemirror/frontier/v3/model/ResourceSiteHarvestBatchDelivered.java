package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One physically observed full hand part; the field job and intent continue. */
public record ResourceSiteHarvestBatchDelivered(ResourceSiteHarvestDeliveryObservation receipt,
                                                int deliveredYieldBefore) implements FrontierPayload {
    public ResourceSiteHarvestBatchDelivered {
        Objects.requireNonNull(receipt, "field batch delivery receipt");
        if (deliveredYieldBefore < 0 || deliveredYieldBefore % 64 != 0 || receipt.harvestedQuantity() != 64
                || !receipt.id().equals(observationId(receipt.jobId(), deliveredYieldBefore)))
            throw new IllegalArgumentException("field intermediate delivery must name one exact full part");
    }

    public static PhysicalObservationId observationId(SubjectId jobId, int deliveredYieldBefore) {
        Objects.requireNonNull(jobId, "field batch job");
        if (!jobId.value().startsWith("job:site-harvest-")
                || deliveredYieldBefore < 0 || deliveredYieldBefore % 64 != 0)
            throw new IllegalArgumentException("field batch observation needs its stable job and part offset");
        return new PhysicalObservationId("observation:field-delivery-" + jobId.value().substring("job:".length())
                + "-part-" + deliveredYieldBefore / 64);
    }

    @Override public String type() { return "frontier.resource_site_harvest_batch_delivered"; }
}
