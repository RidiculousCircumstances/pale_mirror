package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Explicit land/resource owner and storage identity; a container does not become the economic owner. */
public record ExtractionSite(SubjectId id, SubjectId settlementId, SubjectId containerId,
                             ExtractionLayout layout) {
    public ExtractionSite {
        Objects.requireNonNull(id); Objects.requireNonNull(settlementId); Objects.requireNonNull(containerId);
        Objects.requireNonNull(layout);
        if (id.equals(settlementId) || id.equals(containerId) || settlementId.equals(containerId))
            throw new IllegalArgumentException("extraction identities must be distinct");
    }
}
