package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** The field adapter has durably retired the exact accepted physical effect. */
public record ResourceSiteHarvestWorkAcknowledged(ResourceSiteHarvestWorkAcceptance acceptance) implements FrontierPayload {
    public ResourceSiteHarvestWorkAcknowledged { Objects.requireNonNull(acceptance, "retired field acceptance"); }
    @Override public String type() { return "frontier.resource_site_harvest_work_acknowledged"; }
}
