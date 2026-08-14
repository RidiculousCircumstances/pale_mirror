package io.farfrontier.palemirror.api;

import java.util.Objects;

/** Stable physical carrier assignment to semantic building slots. */
public record ResidentSeed(String residentId, String nameKey, String cohort, String role,
                           String homeBuildingId, String homeSlotId,
                           String workplaceBuildingId, String workplaceSlotId,
                           VisualPoint home, VisualPoint workplace) {
    public ResidentSeed {
        if (residentId == null || residentId.isBlank()) throw new IllegalArgumentException("residentId is required");
        if (nameKey == null || nameKey.isBlank()) throw new IllegalArgumentException("nameKey is required");
        if (cohort == null || cohort.isBlank()) throw new IllegalArgumentException("cohort is required");
        if (role == null || !role.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("resident role must be a namespaced id");
        }
        if (homeBuildingId == null || homeBuildingId.isBlank() || homeSlotId == null || homeSlotId.isBlank()) {
            throw new IllegalArgumentException("resident home building and slot are required");
        }
        workplaceBuildingId = workplaceBuildingId == null ? "" : workplaceBuildingId;
        workplaceSlotId = workplaceSlotId == null ? "" : workplaceSlotId;
        if (workplaceBuildingId.isBlank() != workplaceSlotId.isBlank()) {
            throw new IllegalArgumentException("resident workplace building and slot must be present together");
        }
        Objects.requireNonNull(home, "home");
        if (!workplaceBuildingId.isBlank()) Objects.requireNonNull(workplace, "workplace");
    }
}
