package io.farfrontier.palemirror.api;

import java.util.List;
import java.util.Objects;

/** Immutable physical genesis fact. Core validates and copies it before creating canonical state. */
public record AuthoredRegionSeed(String planId, String archetypeId, int definitionVersion, String contentHash,
                                 String dimensionId, String climate, String palette, VisualPoint anchor,
                                 VisualBounds settlementBounds, VisualPoint freightGate, VisualPoint receivingDepot,
                                 AuthoredMineSitePlan primaryMineSite, AuthoredMineSitePlan alternateMineSite,
                                 List<VisualPoint> baselineRailNodes, List<VisualModulePlacement> modules,
                                 List<ResidentSeed> residents, List<VisualBounds> expansionPlots,
                                 List<VisualPoint> shelterCandidates) {
    public AuthoredRegionSeed {
        require(planId, "planId");
        require(archetypeId, "archetypeId");
        require(contentHash, "contentHash");
        require(dimensionId, "dimensionId");
        require(climate, "climate");
        require(palette, "palette");
        if (definitionVersion < 1) throw new IllegalArgumentException("definitionVersion must be positive");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(settlementBounds, "settlementBounds");
        Objects.requireNonNull(freightGate, "freightGate");
        Objects.requireNonNull(receivingDepot, "receivingDepot");
        Objects.requireNonNull(primaryMineSite, "primaryMineSite");
        Objects.requireNonNull(alternateMineSite, "alternateMineSite");
        if (primaryMineSite.role() != AuthoredMineRole.PRIMARY
                || alternateMineSite.role() != AuthoredMineRole.ALTERNATE) {
            throw new IllegalArgumentException("Authored region requires primary and alternate MineSite roles");
        }
        baselineRailNodes = List.copyOf(baselineRailNodes);
        modules = List.copyOf(modules);
        residents = List.copyOf(residents);
        expansionPlots = List.copyOf(expansionPlots);
        shelterCandidates = List.copyOf(shelterCandidates);
        if (residents.size() != 48) throw new IllegalArgumentException("Authored frontier requires exactly 48 residents");
        if (expansionPlots.size() < 6) throw new IllegalArgumentException("Authored frontier requires six expansion plots");
        if (baselineRailNodes.size() < 2) throw new IllegalArgumentException("Baseline railway requires a path");
        if (shelterCandidates.size() < 2) throw new IllegalArgumentException("Authored frontier requires shelter candidates");
    }

    public VisualPoint primaryMine() { return primaryMineSite.portal(); }
    public VisualPoint alternateMine() { return alternateMineSite.portal(); }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
