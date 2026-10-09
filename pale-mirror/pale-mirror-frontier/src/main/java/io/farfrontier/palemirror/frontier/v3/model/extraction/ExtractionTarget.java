package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.model.CellMutationKey;
import java.util.Objects;

/** Exact admitted source generation. Neither position nor material supplies its owner. */
public record ExtractionTarget(CellMutationKey key, long revision) {
    public ExtractionTarget {
        Objects.requireNonNull(key);
        if (key.family() != CellMutationKey.OwnerFamily.EXTRACTIVE_SITE || revision < 1)
            throw new IllegalArgumentException("extraction target requires its exact owner family and source generation");
    }
}
