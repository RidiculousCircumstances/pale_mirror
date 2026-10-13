package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Frozen before-effect fuel portion; machine revision rejects late or duplicate receipts. */
public record ProductionStationFuelBurn(SubjectId stationId, SubjectId containerId, SubjectId accountId,
        long machineRevision, long authorityEpoch, String itemKind, Map<SubjectId, Integer> lots,
        long heat, List<MaterialSourceSelection.Slice> source) {
    public ProductionStationFuelBurn {
        Objects.requireNonNull(stationId); Objects.requireNonNull(containerId); Objects.requireNonNull(accountId);
        lots = Map.copyOf(lots); source = List.copyOf(source);
        if (machineRevision < 1 || authorityEpoch < 0 || heat < 1 || heat > 1_000_000_000L
                || itemKind == null || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")
                || lots.isEmpty() || lots.values().stream().anyMatch(value -> value < 1)
                || lots.values().stream().reduce(0, Math::addExact) != 1
                || authorityEpoch == 0 && !source.isEmpty()
                || authorityEpoch > 0 && (source.size() != 1 || source.getFirst().epoch() != authorityEpoch
                    || source.getFirst().moved() != 1 || !(source.getFirst().address() instanceof PhysicalStackAddress.ContainerSlot slot)
                    || !slot.slot().containerId().equals(containerId)))
            throw new IllegalArgumentException("invalid exact station fuel burn preimage");
    }
}
