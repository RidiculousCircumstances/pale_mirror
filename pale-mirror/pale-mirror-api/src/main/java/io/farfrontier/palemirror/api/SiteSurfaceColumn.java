package io.farfrontier.palemirror.api;

import java.util.Objects;

/** Absolute first-air datum and exclusive semantic owner for a managed site column. */
public record SiteSurfaceColumn(int x, int z, int groundY, SiteSurfaceUse use, String ownerId) {
    public SiteSurfaceColumn {
        Objects.requireNonNull(use, "use");
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId is required");
    }
}
