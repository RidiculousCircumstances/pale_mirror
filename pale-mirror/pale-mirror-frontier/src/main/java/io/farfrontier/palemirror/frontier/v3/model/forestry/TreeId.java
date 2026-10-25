package io.farfrontier.palemirror.frontier.v3.model.forestry;

import java.util.Objects;
import java.util.UUID;

/** Natural object identity. Neither a settlement title nor a position-derived dispatch key. */
public record TreeId(UUID value) implements Comparable<TreeId> {
    public TreeId { Objects.requireNonNull(value); }
    @Override public int compareTo(TreeId other) { return value.compareTo(other.value); }
}
