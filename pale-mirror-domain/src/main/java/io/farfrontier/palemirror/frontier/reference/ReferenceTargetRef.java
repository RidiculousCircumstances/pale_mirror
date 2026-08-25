package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Immutable Python {@code TargetRef}; an id takes precedence over a cell coordinate in its wire key. */
public record ReferenceTargetRef(ReferenceTargetKind kind, Integer id, Integer x, Integer y) {
    public ReferenceTargetRef { Objects.requireNonNull(kind, "kind"); }
    public ReferenceTargetRef(ReferenceTargetKind kind, int id) { this(kind, id, null, null); }
    public static ReferenceTargetRef cell(int x, int y) { return new ReferenceTargetRef(ReferenceTargetKind.CELL, null, x, y); }
    public String key() { return id != null ? kind.id() + ":" + id : kind.id() + ":" + x + "," + y; }
}
