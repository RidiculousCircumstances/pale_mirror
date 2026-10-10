package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Set;

/** Exact development admission; never a block mutation, extraction receipt or stock issue. */
public record ExtractionFrontierOpened(SubjectId siteId, long expectedRevision, Set<Long> cells) implements FrontierPayload {
    public ExtractionFrontierOpened {
        java.util.Objects.requireNonNull(siteId); cells = Set.copyOf(cells);
        if (expectedRevision < 1 || cells.isEmpty() || cells.size() > WorkAreaDevelopment.MAX_CELLS
                || cells.stream().anyMatch(id -> id < 1)) throw new IllegalArgumentException("invalid frontier opening");
    }
    @Override public String type() { return "frontier.extraction_frontier_opened"; }
}
