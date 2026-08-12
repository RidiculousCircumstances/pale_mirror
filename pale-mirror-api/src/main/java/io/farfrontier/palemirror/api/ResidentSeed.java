package io.farfrontier.palemirror.api;

import java.util.Objects;

public record ResidentSeed(String residentId, String nameKey, String cohort, String role,
                           VisualPoint home, VisualPoint workplace) {
    public ResidentSeed {
        if (residentId == null || residentId.isBlank()) throw new IllegalArgumentException("residentId is required");
        if (nameKey == null || nameKey.isBlank()) throw new IllegalArgumentException("nameKey is required");
        if (cohort == null || cohort.isBlank()) throw new IllegalArgumentException("cohort is required");
        if (role == null || role.isBlank()) throw new IllegalArgumentException("role is required");
        Objects.requireNonNull(home, "home");
    }
}
