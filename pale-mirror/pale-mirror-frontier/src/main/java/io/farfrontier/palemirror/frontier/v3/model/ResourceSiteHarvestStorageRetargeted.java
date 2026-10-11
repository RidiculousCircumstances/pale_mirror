package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;

/** Farmer-owner replacement of an unbegun storage obligation, never a rewrite of an old effect. */
public record ResourceSiteHarvestStorageRetargeted(SubjectId siteId, SubjectId jobId, SubjectId workerId,
        long expectedRevision, InventoryCustody.ContainerSlot destination, PhysicalIntentId replacementIntent) implements FrontierPayload {
    public ResourceSiteHarvestStorageRetargeted {
        Objects.requireNonNull(siteId); Objects.requireNonNull(jobId); Objects.requireNonNull(workerId);
        Objects.requireNonNull(destination); Objects.requireNonNull(replacementIntent);
        if (expectedRevision < 1) throw new IllegalArgumentException("field storage replacement needs its exact predecessor version");
    }
    @Override public String type() { return "frontier.resource_site_harvest_storage_retargeted"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
