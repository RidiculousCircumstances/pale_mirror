package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Explicit owner-certified reclassification; no physical write or authority transfer by itself. */
public record WorksiteBlockTransition(WorksiteBlock predecessor, WorksiteBlock successor) {
    public WorksiteBlockTransition {
        Objects.requireNonNull(predecessor); Objects.requireNonNull(successor);
        if (!predecessor.position().equals(successor.position())
                || predecessor.key().family() != successor.key().family()
                || !predecessor.key().owner().equals(successor.key().owner())
                || predecessor.key().equals(successor.key()))
            throw new IllegalArgumentException("worksite reclassification must retain its exact physical owner and position");
    }
}
