package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One durable HOT-to-COLD boundary: observed farmer hand and scene exit commit together. */
public record ResourceSiteHarvestHandRelease(SubjectId siteId, SubjectId jobId, SubjectId actorAccountId,
                                             long actorEpoch, FungiblePhysicalObservation.Stack observedHand,
                                             SceneLeaseReleased sceneRelease) implements FrontierPayload {
    public ResourceSiteHarvestHandRelease {
        Objects.requireNonNull(siteId, "harvest hand release site");
        Objects.requireNonNull(jobId, "harvest hand release job");
        Objects.requireNonNull(actorAccountId, "harvest hand release account");
        Objects.requireNonNull(observedHand, "harvest hand release observation");
        Objects.requireNonNull(sceneRelease, "harvest hand release scene");
        if (actorEpoch < 1 || !(observedHand.address() instanceof PhysicalStackAddress.ActorHand)
                || !observedHand.itemKind().equals("minecraft:wheat")) {
            throw new IllegalArgumentException("harvest hand release needs one current wheat hand");
        }
    }

    @Override public String type() { return "frontier.resource_site_harvest_hand_release"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
