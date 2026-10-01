package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Fresh inspection of the exact isolated farmer and its already bound terminal crop batch. */
public record ResourceSiteHarvestSceneReconciled(SubjectId siteId, SubjectId jobId, SceneLeaseId leaseId,
        long leaseRevision, long bodyEpoch, BodyPosition observedBody,
        FungiblePhysicalObservation.Stack observedHand) implements FrontierPayload {
    public ResourceSiteHarvestSceneReconciled {
        Objects.requireNonNull(siteId); Objects.requireNonNull(jobId); Objects.requireNonNull(leaseId);
        Objects.requireNonNull(observedBody); Objects.requireNonNull(observedHand);
        if (leaseRevision < 1 || bodyEpoch < 1 || !(observedHand.address() instanceof PhysicalStackAddress.ActorHand))
            throw new IllegalArgumentException("harvest reconciliation needs an exact body and hand epoch");
    }
    @Override public String type() { return "frontier.resource_site_harvest_scene_reconciled"; }
}
