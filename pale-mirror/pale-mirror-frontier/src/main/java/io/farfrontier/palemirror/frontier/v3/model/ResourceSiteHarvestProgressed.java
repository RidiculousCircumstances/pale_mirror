package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Observed completion of the one durable pending crop by the job's exact named farmer. */
public record ResourceSiteHarvestProgressed(SubjectId jobId, int completedCropSlots) implements FrontierPayload {
    public ResourceSiteHarvestProgressed {
        Objects.requireNonNull(jobId, "resource-site harvest progress job");
        if (!jobId.value().startsWith("job:site-harvest-") || completedCropSlots < 1
                || completedCropSlots > ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS) {
            throw new IllegalArgumentException("resource-site harvest progress is invalid");
        }
    }

    @Override public String type() { return "frontier.resource_site_harvest_progressed"; }
}
