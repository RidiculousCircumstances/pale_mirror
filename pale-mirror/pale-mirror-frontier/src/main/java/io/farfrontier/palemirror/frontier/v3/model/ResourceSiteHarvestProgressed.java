package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One durable exact-farmer crop receipt: observed in HOT or semantically completed by bounded COLD work. */
public record ResourceSiteHarvestProgressed(SubjectId jobId, int completedCropSlots, String coldScheduleId, long coldDueAt) implements FrontierPayload {
    public ResourceSiteHarvestProgressed {
        Objects.requireNonNull(jobId, "resource-site harvest progress job");
        if (!jobId.value().startsWith("job:site-harvest-") || completedCropSlots < 1
                || completedCropSlots > ResourceSiteHarvestProgress.TOTAL_CROP_SLOTS) {
            throw new IllegalArgumentException("resource-site harvest progress is invalid");
        }
        coldScheduleId = Objects.requireNonNull(coldScheduleId, "resource-site harvest progress COLD schedule");
        if ((coldScheduleId.equals("not_captured")) != (coldDueAt == -1L) || coldDueAt < -1L) {
            throw new IllegalArgumentException("resource-site harvest progress has invalid COLD schedule evidence");
        }
    }
    public ResourceSiteHarvestProgressed(SubjectId jobId, int completedCropSlots) { this(jobId, completedCropSlots, "not_captured", -1L); }
    public ResourceSiteHarvestProgressed(SubjectId jobId, int completedCropSlots, ScheduleId coldScheduleId, long coldDueAt) {
        this(jobId, completedCropSlots, Objects.requireNonNull(coldScheduleId, "resource-site harvest progress COLD schedule").value(), coldDueAt);
    }

    @Override public String type() { return "frontier.resource_site_harvest_progressed"; }
}
