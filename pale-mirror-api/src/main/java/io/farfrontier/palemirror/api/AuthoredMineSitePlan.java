package io.farfrontier.palemirror.api;

import java.util.List;
import java.util.Objects;

/** Immutable mountain-native MineSite geometry and semantic contract. */
public record AuthoredMineSitePlan(String siteId, AuthoredMineRole role, VisualPoint portal,
                                   VisualPoint loadingEndpoint, VisualPoint controllerAnchor,
                                   VisualBounds bounds, int inwardQuarterTurns,
                                   List<AuthoredBuildingPlan> surfaceBuildings,
                                   List<VisualModulePlacement> undergroundModules,
                                   List<StagedVisualModule> stagedModules,
                                   List<MineFoundationPlan> foundations,
                                   List<SemanticVisualVolume> semanticVolumes) {
    public AuthoredMineSitePlan {
        if (siteId == null || siteId.isBlank()) throw new IllegalArgumentException("siteId is required");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(portal, "portal");
        Objects.requireNonNull(loadingEndpoint, "loadingEndpoint");
        Objects.requireNonNull(controllerAnchor, "controllerAnchor");
        Objects.requireNonNull(bounds, "bounds");
        inwardQuarterTurns = Math.floorMod(inwardQuarterTurns, 4);
        surfaceBuildings = List.copyOf(surfaceBuildings);
        undergroundModules = List.copyOf(undergroundModules);
        stagedModules = List.copyOf(stagedModules);
        foundations = List.copyOf(foundations);
        semanticVolumes = List.copyOf(semanticVolumes);
        if (surfaceBuildings.isEmpty() || undergroundModules.isEmpty()) {
            throw new IllegalArgumentException("MineSite requires surface buildings and underground modules");
        }
        if (surfaceBuildings.stream().map(AuthoredBuildingPlan::buildingId).distinct().count()
                != surfaceBuildings.size()) {
            throw new IllegalArgumentException("MineSite surface building ids must be unique");
        }
        if (foundations.isEmpty()) throw new IllegalArgumentException("MineSite requires surface foundations");
        if (foundations.stream().map(MineFoundationPlan::id).distinct().count() != foundations.size()) {
            throw new IllegalArgumentException("MineSite foundation ids must be unique");
        }
        var foundationIds = foundations.stream().map(MineFoundationPlan::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (surfaceBuildings.stream().flatMap(building -> building.modules().stream())
                .anyMatch(module -> !foundationIds.contains(module.foundationId()))) {
            throw new IllegalArgumentException("MineSite surface module references an unknown foundation");
        }
        if (surfaceBuildings.stream().anyMatch(building -> !contains(bounds, building.parcel()))
                || undergroundModules.stream().anyMatch(module -> !contains(bounds, module.footprint()))) {
            throw new IllegalArgumentException("MineSite authored module escaped its site bounds");
        }
        if (semanticVolumes.stream().noneMatch(value -> value.purpose().equals("INFECTION"))) {
            throw new IllegalArgumentException("MineSite requires an INFECTION semantic volume");
        }
    }

    public List<VisualModulePlacement> initialModules() {
        return java.util.stream.Stream.concat(surfaceBuildings.stream()
                        .flatMap(building -> building.modules().stream()), undergroundModules.stream()).toList();
    }

    private static boolean contains(VisualBounds outer, VisualBounds inner) {
        return outer.contains(inner.min()) && outer.contains(inner.max());
    }
}
