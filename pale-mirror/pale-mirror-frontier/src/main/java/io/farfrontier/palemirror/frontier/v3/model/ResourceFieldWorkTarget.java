package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Exact plant-generation target. Physical age and a list index cannot identify an operation. */
public record ResourceFieldWorkTarget(SubjectId siteId, long layoutRevision,
                                      ResourceFieldLayout.CellId cellId, long generation) {
    public ResourceFieldWorkTarget {
        Objects.requireNonNull(siteId);
        Objects.requireNonNull(cellId);
        if (layoutRevision < 1 || generation < 0)
            throw new IllegalArgumentException("field target has an invalid revision or generation");
    }
    public boolean current(ResourceFieldCycle field) {
        return siteId.equals(field.siteId()) && layoutRevision == field.layout().revision()
                && field.layout().cell(cellId).isPresent() && generation == field.generation(cellId);
    }
}
