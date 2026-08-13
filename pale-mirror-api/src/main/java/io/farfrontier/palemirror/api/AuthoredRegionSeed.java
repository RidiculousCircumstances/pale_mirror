package io.farfrontier.palemirror.api;

import java.util.List;
import java.util.Objects;

/** Immutable physical genesis fact. Core validates and copies it before creating canonical state. */
public record AuthoredRegionSeed(String planId, String archetypeId, int definitionVersion, String contentHash,
                                 String dimensionId, String climate, String palette, VisualPoint anchor,
                                 AuthoredSettlementSitePlan settlementSite,
                                 AuthoredMineSitePlan primaryMineSite, AuthoredMineSitePlan alternateMineSite,
                                 List<VisualPoint> baselineRailNodes, List<ResidentSeed> residents) {
    public AuthoredRegionSeed {
        require(planId, "planId");
        require(archetypeId, "archetypeId");
        require(contentHash, "contentHash");
        require(dimensionId, "dimensionId");
        require(climate, "climate");
        require(palette, "palette");
        if (definitionVersion < 1) throw new IllegalArgumentException("definitionVersion must be positive");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(settlementSite, "settlementSite");
        Objects.requireNonNull(primaryMineSite, "primaryMineSite");
        Objects.requireNonNull(alternateMineSite, "alternateMineSite");
        if (primaryMineSite.role() != AuthoredMineRole.PRIMARY
                || alternateMineSite.role() != AuthoredMineRole.ALTERNATE) {
            throw new IllegalArgumentException("Authored region requires primary and alternate MineSite roles");
        }
        baselineRailNodes = List.copyOf(baselineRailNodes);
        residents = List.copyOf(residents);
        if (residents.size() != 48) throw new IllegalArgumentException("Authored frontier requires exactly 48 residents");
        if (baselineRailNodes.size() < 2) throw new IllegalArgumentException("Baseline railway requires a path");
    }

    public VisualPoint primaryMine() { return primaryMineSite.portal(); }
    public VisualPoint alternateMine() { return alternateMineSite.portal(); }
    public VisualBounds settlementBounds() { return settlementSite.bounds(); }
    public VisualPoint freightGate() { return settlementSite.freightGate(); }
    public VisualPoint receivingDepot() { return settlementSite.receivingDepot(); }
    public List<VisualModulePlacement> modules() { return settlementSite.modules(); }
    public List<VisualBounds> expansionPlots() { return settlementSite.expansionPlots(); }
    public List<VisualPoint> shelterCandidates() { return settlementSite.shelterCandidates(); }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
