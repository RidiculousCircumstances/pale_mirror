package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** Exact isolated scene recovery plus its already-applied crop/hand receipt, one atomic outcome. */
public record ResourceSiteHarvestEffectReconciled(ResourceSiteHarvestSceneReconciled recovery,
        ResourceSiteHarvestProgressed applied) implements FrontierPayload {
    public ResourceSiteHarvestEffectReconciled {
        Objects.requireNonNull(recovery); Objects.requireNonNull(applied);
        if (!recovery.siteId().equals(applied.siteId()) || !recovery.jobId().equals(applied.jobId())
                || applied.observedHand().isEmpty())
            throw new IllegalArgumentException("harvest effect recovery requires the same observed physical job");
    }
    @Override public String type() { return "frontier.resource_site_harvest_effect_reconciled"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
