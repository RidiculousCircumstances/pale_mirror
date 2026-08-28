package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable physical evidence that is intentionally retained rather than repaired or adopted. */
public record InventoryConflictObserved(InventoryConflict conflict) implements FrontierPayload {
    public InventoryConflictObserved { Objects.requireNonNull(conflict, "inventory conflict"); }
    @Override public String type() { return "frontier.inventory_conflict_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
