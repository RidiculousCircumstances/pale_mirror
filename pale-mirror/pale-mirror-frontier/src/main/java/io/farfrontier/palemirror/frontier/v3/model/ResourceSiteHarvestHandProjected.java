package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Observed HOT binding of a COLD-carried field part to the same named farmer's offhand. */
public record ResourceSiteHarvestHandProjected(SubjectId siteId, SubjectId jobId, SubjectId actorAccountId,
                                               SceneLeaseId leaseId, long actorEpoch,
                                               FungiblePhysicalObservation.Stack hand) implements FrontierPayload {
    public ResourceSiteHarvestHandProjected {
        Objects.requireNonNull(siteId, "field hand site"); Objects.requireNonNull(jobId, "field hand job");
        Objects.requireNonNull(actorAccountId, "field hand account"); Objects.requireNonNull(leaseId, "field hand scene");
        Objects.requireNonNull(hand, "field hand observation");
        if (!siteId.value().startsWith("site:") || !jobId.value().startsWith("job:site-harvest-")
                || !actorAccountId.value().startsWith("custody:field-actor-") || actorEpoch < 1
                || !(hand.address() instanceof PhysicalStackAddress.ActorHand)
                || !hand.itemKind().equals("minecraft:wheat")) {
            throw new IllegalArgumentException("field hand projection lacks its declared worker account or wheat body");
        }
    }
    @Override public String type() { return "frontier.resource_site_harvest_hand_projected"; }
}
