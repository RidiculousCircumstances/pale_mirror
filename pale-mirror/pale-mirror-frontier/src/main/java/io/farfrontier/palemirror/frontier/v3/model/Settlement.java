package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** A named autonomous settlement and its exact bootstrap residents and structures. */
public record Settlement(
        SubjectId id, String displayName, BlockPosition anchor,
        List<Resident> residents, List<SettlementStructure> structures
) {
    public Settlement {
        Objects.requireNonNull(id, "settlement id");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("settlement display name is required");
        Objects.requireNonNull(anchor, "settlement anchor");
        residents = List.copyOf(residents);
        structures = List.copyOf(structures);
        if (residents.size() < 20 || residents.size() > 40) throw new IllegalArgumentException("settlement must start with 20 to 40 residents");
        var kinds = structures.stream().map(SettlementStructure::kind).collect(java.util.stream.Collectors.toSet());
        if (kinds.size() != structures.size() || !kinds.containsAll(java.util.Set.of(StructureKind.HALL,
                StructureKind.HOUSING, StructureKind.FARM, StructureKind.WORKSHOP, StructureKind.DEPOT, StructureKind.INFIRMARY)))
            throw new IllegalArgumentException("settlement requires unique facilities and its core bootstrap functions");
        if (residents.stream().anyMatch(resident -> !id.equals(resident.settlementId()))
                || structures.stream().anyMatch(structure -> !id.equals(structure.settlementId()))) {
            throw new IllegalArgumentException("settlement child ownership does not match");
        }
    }
}
