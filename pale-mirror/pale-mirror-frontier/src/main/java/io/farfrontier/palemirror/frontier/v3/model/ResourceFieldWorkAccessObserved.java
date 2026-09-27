package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Exact loaded-world headroom observation for one stable field work target. */
public record ResourceFieldWorkAccessObserved(SubjectId siteId, long epoch, long layoutRevision,
                                              ResourceFieldLayout.CellId cellId, BlockPosition headroom,
                                              boolean blocked, String observedBlock,
                                              Optional<SceneLeaseId> hotLeaseId) implements FrontierPayload {
    public ResourceFieldWorkAccessObserved {
        Objects.requireNonNull(siteId, "work access site");
        Objects.requireNonNull(cellId, "work access cell");
        Objects.requireNonNull(headroom, "work access headroom");
        Objects.requireNonNull(observedBlock, "work access observed block");
        hotLeaseId = Objects.requireNonNull(hotLeaseId, "work access scene");
        if (epoch < 0 || layoutRevision < 1 || observedBlock.isBlank()
                || observedBlock.length() > 128 || blocked && observedBlock.equals("minecraft:air"))
            throw new IllegalArgumentException("field work access lacks an exact physical observation");
    }

    @Override public String type() { return "frontier.resource_field_work_access_observed"; }
}
