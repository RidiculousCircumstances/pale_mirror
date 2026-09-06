package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded exact physical-damage summary for one canonical settlement structure. */
public record StructureDamage(SubjectId structureId, Map<BlockPosition, DamageCell> cells) {
    public static final int MAX_CELLS = 1_024;

    public StructureDamage {
        Objects.requireNonNull(structureId, "structure id");
        Objects.requireNonNull(cells, "damage cells");
        if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("structure damage cell limit exceeded");
        cells = Map.copyOf(cells);
        cells.forEach((position, cell) -> {
            Objects.requireNonNull(position, "damage position");
            Objects.requireNonNull(cell, "damage cell");
        });
    }

    public static StructureDamage empty(SubjectId structureId) { return new StructureDamage(structureId, Map.of()); }

    /** Duplicate physical evidence is idempotent only when it names the same semantic cell. */
    public StructureDamage record(BlockPosition position, GrayboxSemanticPart part, String cause) {
        DamageCell incoming = new DamageCell(part, cause);
        DamageCell prior = cells.get(position);
        if (prior != null) {
            if (!prior.equals(incoming)) throw new IllegalArgumentException("damage cell was observed with conflicting semantic evidence");
            return this;
        }
        if (cells.size() >= MAX_CELLS) throw new IllegalArgumentException("structure damage cell limit exceeded");
        Map<BlockPosition, DamageCell> next = new LinkedHashMap<>(cells);
        next.put(position, incoming);
        return new StructureDamage(structureId, next);
    }

    /** Removes only an already-recorded exact cell after a verified physical repair. */
    public StructureDamage repair(BlockPosition position, GrayboxSemanticPart part) {
        DamageCell current = cells.get(Objects.requireNonNull(position, "repair position"));
        if (current == null || current.semanticPart() != Objects.requireNonNull(part, "repair semantic part")) {
            throw new IllegalArgumentException("repair does not match recorded structure damage");
        }
        Map<BlockPosition, DamageCell> next = new LinkedHashMap<>(cells); next.remove(position);
        return new StructureDamage(structureId, next);
    }

    /** One exact affected block and its first immutable observed cause. */
    public record DamageCell(GrayboxSemanticPart semanticPart, String cause) {
        public DamageCell {
            Objects.requireNonNull(semanticPart, "semantic part");
            Objects.requireNonNull(cause, "damage cause");
            if (cause.isBlank() || cause.length() > 160) throw new IllegalArgumentException("damage cause must be bounded and non-blank");
        }
    }
}
