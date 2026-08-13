package io.farfrontier.palemirror.api;

import java.util.Objects;

/** Named physical volume whose meaning survives blueprint and palette changes. */
public record SemanticVisualVolume(String id, String purpose, VisualBounds bounds) {
    public SemanticVisualVolume {
        require(id, "id");
        require(purpose, "purpose");
        Objects.requireNonNull(bounds, "bounds");
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
