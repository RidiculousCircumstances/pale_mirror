package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact input while a production job owns it, either canonically or in a loaded depot. */
sealed interface ProductionInputHold permits ProductionInputHold.Cold, ProductionInputHold.Materialized {
    SubjectId itemId();

    /** The COLD job is its item's sole canonical holder; the stack is not in ExactInventory. */
    record Cold(ExactItemStack item) implements ProductionInputHold {
        public Cold { Objects.requireNonNull(item, "cold production input"); }
        @Override public SubjectId itemId() { return item.id(); }
    }

    /** The HOT job references the one exact stack retained in its owned Minecraft depot slot. */
    record Materialized(SubjectId itemId) implements ProductionInputHold {
        public Materialized { Objects.requireNonNull(itemId, "materialized production input"); }
    }
}
