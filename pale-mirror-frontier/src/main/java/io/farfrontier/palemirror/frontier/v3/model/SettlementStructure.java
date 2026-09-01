package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Stable semantic structure identity and plan-owned exterior orientation. */
public record SettlementStructure(SubjectId id, SubjectId settlementId, StructureKind kind, BlockPosition anchor,
                                  FacilityFacing facing) {
    public SettlementStructure {
        Objects.requireNonNull(id, "structure id");
        Objects.requireNonNull(settlementId, "settlement id");
        Objects.requireNonNull(kind, "structure kind");
        Objects.requireNonNull(anchor, "structure anchor");
        Objects.requireNonNull(facing, "structure facing");
    }
}
