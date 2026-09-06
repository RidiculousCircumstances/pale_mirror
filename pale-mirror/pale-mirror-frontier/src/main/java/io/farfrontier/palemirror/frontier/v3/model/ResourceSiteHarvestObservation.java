package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact observed receipt for a 64-cell wheat transformation and its sole output stack. */
public record ResourceSiteHarvestObservation(PhysicalObservationId id, PhysicalIntentId intentId,
                                             SubjectId siteId, SubjectId workerId, ExactItemStack output,
                                             int harvestedCropSlots) implements PhysicalEffectObservation {
    public ResourceSiteHarvestObservation {
        Objects.requireNonNull(id, "observation id"); Objects.requireNonNull(intentId, "intent id");
        Objects.requireNonNull(siteId, "site id"); Objects.requireNonNull(workerId, "worker id"); Objects.requireNonNull(output, "output");
        if (!siteId.value().startsWith("site:") || !workerId.value().startsWith("resident:") || harvestedCropSlots != 64) {
            throw new IllegalArgumentException("resource-site harvest receipt must retain exactly 64 named crop slots");
        }
    }
}
