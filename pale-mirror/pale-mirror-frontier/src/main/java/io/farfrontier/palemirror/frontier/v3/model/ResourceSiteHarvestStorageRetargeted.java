package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt;

/** Farmer-owner replacement of an unbegun storage obligation, never a rewrite of an old effect. */
public record ResourceSiteHarvestStorageRetargeted(SubjectId siteId, SubjectId jobId, SubjectId workerId,
        long expectedRevision, InventoryCustody.ContainerSlot destination, PhysicalIntentId replacementIntent,
        ActorExecutionId executionId, Optional<PedestrianRouteReceipt> plannedRoute) implements FrontierPayload {
    public ResourceSiteHarvestStorageRetargeted {
        Objects.requireNonNull(siteId); Objects.requireNonNull(jobId); Objects.requireNonNull(workerId);
        Objects.requireNonNull(destination); Objects.requireNonNull(replacementIntent);
        Objects.requireNonNull(executionId); Objects.requireNonNull(plannedRoute);
        if (expectedRevision < 1 || !executionId.actorId().equals(workerId) || !executionId.activityOwnerId().equals(jobId)
                || executionId.activityKind() != ActorActivityKind.FIELD_HARVEST)
            throw new IllegalArgumentException("field storage replacement needs its exact predecessor version and authority");
    }
    @Override public String type() { return "frontier.resource_site_harvest_storage_retargeted"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
