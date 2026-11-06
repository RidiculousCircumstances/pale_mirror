package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObservation;
import java.util.Objects;

/** Confirmed physical cell fact, shared by external changes and future repair. No mutation permission. */
public record StructureCellObserved(SubjectId structureId, BlockPosition position, GrayboxSemanticPart part,
        NavigationObservation observation, boolean originalMaterial, String cause) implements FrontierPayload {
    public StructureCellObserved {
        Objects.requireNonNull(structureId); Objects.requireNonNull(position); Objects.requireNonNull(part);
        Objects.requireNonNull(observation); observation.requireAt(position);
        if (observation.cell().isEmpty() || cause == null || cause.isBlank() || cause.length() > 160)
            throw new IllegalArgumentException("structural outcome requires known physical evidence and a bounded cause");
    }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
    @Override public String type() { return "frontier.structure_cell_observed"; }
}
