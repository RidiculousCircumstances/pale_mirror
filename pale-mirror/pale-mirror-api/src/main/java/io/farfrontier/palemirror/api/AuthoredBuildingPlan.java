package io.farfrontier.palemirror.api;

import java.util.List;
import java.util.Objects;

/** One functional building, potentially composed from multiple visual modules. */
public record AuthoredBuildingPlan(String buildingId, SettlementDevelopmentStage introducedAt,
                                   SettlementBuildingCategory category, List<BuildingFunctionId> functions,
                                   VisualBounds parcel, List<VisualModulePlacement> modules,
                                   List<BuildingSlot> slots) {
    public AuthoredBuildingPlan {
        if (buildingId == null || buildingId.isBlank()) throw new IllegalArgumentException("buildingId is required");
        Objects.requireNonNull(introducedAt, "introducedAt");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(parcel, "parcel");
        functions = List.copyOf(functions);
        modules = List.copyOf(modules);
        slots = List.copyOf(slots);
        if (functions.isEmpty()) throw new IllegalArgumentException("building functions are required");
        if (modules.isEmpty()) throw new IllegalArgumentException("building modules are required");
        if (slots.stream().map(BuildingSlot::id).distinct().count() != slots.size()) {
            throw new IllegalArgumentException("building slot ids must be unique within " + buildingId);
        }
        if (modules.stream().anyMatch(module -> !contains(parcel, module.footprint()))) {
            throw new IllegalArgumentException("building module escaped parcel " + buildingId);
        }
        if (slots.stream().anyMatch(slot -> !parcel.contains(slot.position()))) {
            throw new IllegalArgumentException("building slot escaped parcel " + buildingId);
        }
    }

    public int capacity(BuildingSlotKind kind) {
        return slots.stream().filter(slot -> slot.kind() == kind).mapToInt(BuildingSlot::capacity).sum();
    }

    private static boolean contains(VisualBounds outer, VisualBounds inner) {
        return outer.contains(inner.min()) && outer.contains(inner.max());
    }
}
