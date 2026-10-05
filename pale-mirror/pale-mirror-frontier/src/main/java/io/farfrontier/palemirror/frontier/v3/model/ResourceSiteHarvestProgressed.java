package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** One durable exact-farmer crop receipt: observed in HOT or semantically completed by bounded COLD work. */
public record ResourceSiteHarvestProgressed(SubjectId siteId, long epoch, SubjectId jobId, int completedCropSlots,
                                            long layoutRevision, ResourceFieldLayout.CellId cellId, long generation,
                                            ResourceFieldCycle.WorkOutcome outcome,
                                            ScheduleId coldScheduleId, long coldDueAt,
                                            Optional<HandObservation> observedHand) implements FrontierPayload {
    /** Only a trusted HOT adapter may supply this exact observed actor body and OFFHAND count. */
    public record HandObservation(PhysicalStackAddress.ActorHand address, long authorityEpoch, int quantity) {
        public HandObservation {
            Objects.requireNonNull(address, "harvest observed actor hand");
            if (authorityEpoch < 1 || quantity < 0 || quantity > 64)
                throw new IllegalArgumentException("harvest observed hand has invalid authority or quantity");
        }
    }

    /** COLD work has no loaded Minecraft hand to observe. */
    public ResourceSiteHarvestProgressed(SubjectId siteId, long epoch, SubjectId jobId, int completedCropSlots,
                                         long layoutRevision, ResourceFieldLayout.CellId cellId, long generation,
                                         ResourceFieldCycle.WorkOutcome outcome,
                                         ScheduleId coldScheduleId, long coldDueAt) {
        this(siteId, epoch, jobId, completedCropSlots, layoutRevision, cellId, generation, outcome,
                coldScheduleId, coldDueAt, Optional.empty());
    }

    public ResourceSiteHarvestProgressed {
        Objects.requireNonNull(siteId, "resource-site harvest progress site");
        Objects.requireNonNull(jobId, "resource-site harvest progress job");
        if (!siteId.value().startsWith("site:") || epoch < 1
                || !jobId.value().startsWith("job:site-harvest-") || completedCropSlots < 1
                || completedCropSlots > ResourceFieldLayout.MAX_CELLS) {
            throw new IllegalArgumentException("resource-site harvest progress is invalid");
        }
        if (layoutRevision < 1 || generation < 0) throw new IllegalArgumentException("resource-site harvest progress has no valid layout/generation");
        Objects.requireNonNull(cellId, "resource-site harvest progress cell");
        Objects.requireNonNull(outcome, "resource-site harvest progress outcome");
        coldScheduleId = Objects.requireNonNull(coldScheduleId, "resource-site harvest progress COLD schedule");
        observedHand = Objects.requireNonNull(observedHand, "resource-site observed worker hand");
        if (coldDueAt < 0L) {
            throw new IllegalArgumentException("resource-site harvest progress has invalid COLD schedule evidence");
        }
    }

    @Override public String type() { return "frontier.resource_site_harvest_progressed"; }
    @Override public boolean requiresDurableBeforeEffect() { return observedHand.isPresent(); }
}
