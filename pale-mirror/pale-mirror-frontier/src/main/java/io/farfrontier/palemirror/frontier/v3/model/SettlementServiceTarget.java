package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact semantic target retained by a service-work aggregate, never selected by an executor. */
public sealed interface SettlementServiceTarget permits SettlementServiceTarget.StructureCell, SettlementServiceTarget.Infection {
    BlockPosition effectPosition(int surfaceY);

    record StructureCell(SubjectId structureId, BlockPosition position) implements SettlementServiceTarget {
        public StructureCell {
            structureId = Objects.requireNonNull(structureId, "service structure id");
            position = Objects.requireNonNull(position, "service structure position");
            if (!structureId.value().startsWith("structure:")) throw new IllegalArgumentException("service structure target must name a structure");
        }
        @Override public BlockPosition effectPosition(int surfaceY) { return position; }
    }

    record Infection(InfectionCell cell) implements SettlementServiceTarget {
        public Infection { cell = Objects.requireNonNull(cell, "service infection cell"); }
        @Override public BlockPosition effectPosition(int surfaceY) { return cell.originAtY(surfaceY); }
    }
}
