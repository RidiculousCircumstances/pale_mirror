package io.farfrontier.palemirror.api;

import java.util.List;
import java.util.Objects;

/** Immutable mountain-native MineSite geometry and semantic contract. */
public record AuthoredMineSitePlan(String siteId, AuthoredMineRole role, VisualPoint portal,
                                   VisualPoint loadingEndpoint, VisualPoint controllerAnchor,
                                   VisualBounds bounds, int inwardQuarterTurns,
                                   List<VisualModulePlacement> initialModules,
                                   List<StagedVisualModule> stagedModules,
                                   List<SemanticVisualVolume> semanticVolumes) {
    public AuthoredMineSitePlan {
        if (siteId == null || siteId.isBlank()) throw new IllegalArgumentException("siteId is required");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(portal, "portal");
        Objects.requireNonNull(loadingEndpoint, "loadingEndpoint");
        Objects.requireNonNull(controllerAnchor, "controllerAnchor");
        Objects.requireNonNull(bounds, "bounds");
        inwardQuarterTurns = Math.floorMod(inwardQuarterTurns, 4);
        initialModules = List.copyOf(initialModules);
        stagedModules = List.copyOf(stagedModules);
        semanticVolumes = List.copyOf(semanticVolumes);
        if (initialModules.isEmpty()) throw new IllegalArgumentException("MineSite requires initial modules");
        if (semanticVolumes.stream().noneMatch(value -> value.purpose().equals("INFECTION"))) {
            throw new IllegalArgumentException("MineSite requires an INFECTION semantic volume");
        }
    }
}
