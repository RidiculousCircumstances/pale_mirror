package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.buildings.*;

import java.util.Objects;

/** Stable semantic structure identity and plan-owned exterior orientation. */
public record SettlementStructure(
        SubjectId id,
        SubjectId settlementId,
        StructureKind kind,
        BlockPosition anchor,
        FacilityFacing facing,
        BuildingPlanRef building) {
    public SettlementStructure {
        Objects.requireNonNull(id, "structure id");
        Objects.requireNonNull(settlementId, "settlement id");
        Objects.requireNonNull(kind, "structure kind");
        Objects.requireNonNull(anchor, "structure anchor");
        Objects.requireNonNull(facing, "structure facing");
        Objects.requireNonNull(building, "declared building plan");
        BuildingCatalog.standard().require(building, kind);
    }

    public BuildingDefinition definition() {
        return BuildingCatalog.standard().require(building, kind);
    }

    public BuildingPlacement placement() {
        return definition().placement(anchor, facing);
    }
}
