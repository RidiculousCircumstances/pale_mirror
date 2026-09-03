package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable authority to change exactly the harvest job's current crop cell. */
public record ResourceSiteHarvestCropPrepared(SubjectId jobId, int cropSlotIndex) implements FrontierPayload {
    public ResourceSiteHarvestCropPrepared {
        Objects.requireNonNull(jobId, "resource-site harvest crop job");
        if (!jobId.value().startsWith("job:site-harvest-") || cropSlotIndex < 0
                || cropSlotIndex >= ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS) {
            throw new IllegalArgumentException("resource-site harvest crop preparation is invalid");
        }
    }

    @Override public String type() { return "frontier.resource_site_harvest_crop_prepared"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
