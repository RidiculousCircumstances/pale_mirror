package io.farfrontier.palemirror.frontier.reference;

/** Exact Python {@code FieldPostKind} vocabulary. */
public enum ReferenceFieldPostKind {
    OBSERVATION("observation_post"),
    CHECKPOINT("checkpoint"),
    STRONGPOINT("strongpoint"),
    FORWARD_BASE("forward_base");

    private final String id;

    ReferenceFieldPostKind(String id) { this.id = id; }
    public String id() { return id; }
}
