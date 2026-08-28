package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable evidence that a uniquely identified physical exact stack was destroyed. */
public record ExactItemDestroyed(SubjectId itemId, InventoryCustody source, String cause) implements FrontierPayload {
    public ExactItemDestroyed {
        Objects.requireNonNull(itemId, "item id");
        Objects.requireNonNull(source, "item source");
        if (!(source instanceof InventoryCustody.ContainerSlot)) {
            throw new IllegalArgumentException("destroyed exact item must have an owned container source");
        }
        if (cause == null || cause.isBlank() || cause.length() > 256) throw new IllegalArgumentException("invalid item destruction cause");
    }

    @Override public String type() { return "frontier.exact_item_destroyed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
