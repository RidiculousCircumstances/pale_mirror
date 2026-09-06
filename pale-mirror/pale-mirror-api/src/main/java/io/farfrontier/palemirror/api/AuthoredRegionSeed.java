package io.farfrontier.palemirror.api;

import java.util.List;
import java.util.Objects;

/** Immutable physical genesis fact. Core validates and copies it before creating canonical state. */
public record AuthoredRegionSeed(String planId, String archetypeId, int definitionVersion, String contentHash,
                                 String dimensionId, String climate, String palette, VisualPoint anchor,
                                 AuthoredSettlementSitePlan settlementSite,
                                 AuthoredMineSitePlan primaryMineSite, AuthoredMineSitePlan alternateMineSite,
                                 List<VisualPoint> baselineRailNodes, List<VisualChunk> discoveryChunks,
                                 List<ResidentSeed> residents) {
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
        discoveryChunks = discoveryChunks.stream().sorted().toList();
        residents = List.copyOf(residents);
        if (discoveryChunks.isEmpty()) throw new IllegalArgumentException("Authored discovery chunks are required");
        if (discoveryChunks.stream().distinct().count() != discoveryChunks.size()) {
            throw new IllegalArgumentException("Authored discovery chunks must be unique");
        }
        if (!discoveryChunks.contains(VisualChunk.containing(anchor))) {
            throw new IllegalArgumentException("Authored discovery chunks must contain the settlement anchor");
        }
        if (residents.size() != settlementSite.stage().population()) {
            throw new IllegalArgumentException("Authored resident count must match settlement stage population");
        }
        if (residents.stream().map(ResidentSeed::residentId).distinct().count() != residents.size()) {
            throw new IllegalArgumentException("Authored resident identities must be unique");
        }
        if (residents.stream().map(value -> value.homeBuildingId() + ":" + value.homeSlotId()).distinct().count()
                != residents.size()) {
            throw new IllegalArgumentException("Authored residents must have exclusive home slots");
        }
        long assignedWorkplaces = residents.stream().filter(value -> !value.workplaceBuildingId().isBlank()).count();
        if (residents.stream().filter(value -> !value.workplaceBuildingId().isBlank())
                .map(value -> value.workplaceBuildingId() + ":" + value.workplaceSlotId()).distinct().count()
                != assignedWorkplaces) {
            throw new IllegalArgumentException("Authored residents must have exclusive workplace slots");
        }
        for (ResidentSeed resident : residents) validateResidentBinding(resident, settlementSite, primaryMineSite);
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

    private static void validateResidentBinding(ResidentSeed resident, AuthoredSettlementSitePlan settlement,
                                                AuthoredMineSitePlan primaryMine) {
        AuthoredBuildingPlan home = settlement.buildings().stream()
                .filter(value -> value.buildingId().equals(resident.homeBuildingId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Resident references unknown home building "
                        + resident.homeBuildingId()));
        BuildingSlot homeSlot = slot(home, resident.homeSlotId());
        if (homeSlot.kind() != BuildingSlotKind.BED || !homeSlot.position().equals(resident.home())) {
            throw new IllegalArgumentException("Resident home binding is not its exact BED slot");
        }
        if (resident.workplaceBuildingId().isBlank()) return;
        AuthoredBuildingPlan workplace = java.util.stream.Stream.concat(settlement.buildings().stream(),
                        primaryMine.surfaceBuildings().stream())
                .filter(value -> value.buildingId().equals(resident.workplaceBuildingId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Resident references unknown workplace building "
                        + resident.workplaceBuildingId()));
        BuildingSlot workplaceSlot = slot(workplace, resident.workplaceSlotId());
        if (workplaceSlot.kind() != BuildingSlotKind.WORKSTATION
                && workplaceSlot.kind() != BuildingSlotKind.PATROL
                || !workplaceSlot.position().equals(resident.workplace())) {
            throw new IllegalArgumentException("Resident workplace binding is not its exact work slot");
        }
    }

    private static BuildingSlot slot(AuthoredBuildingPlan building, String id) {
        return building.slots().stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown slot " + building.buildingId() + ":" + id));
    }
}
