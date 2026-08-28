package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One named exact stack. Count is Minecraft-item count, never an economic aggregate. */
public record ExactItemStack(SubjectId id, String itemKind, int count, InventoryCustody custody) {
    public ExactItemStack {
        Objects.requireNonNull(id, "item stack id");
        if (itemKind == null || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")) throw new IllegalArgumentException("item kind must be namespace:path");
        if (count <= 0 || count > 64) throw new IllegalArgumentException("item stack count must be 1..64");
        Objects.requireNonNull(custody, "item custody");
    }
}
