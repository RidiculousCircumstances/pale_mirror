package io.farfrontier.palemirror.frontier.reference;

/** Exact Python {@code TargetKind} vocabulary. */
public enum ReferenceTargetKind {
    SETTLEMENT("settlement"),
    SITE("site"),
    NEST("nest"),
    ROUTE("route"),
    CELL("cell"),
    FIELD_POST("field_post");

    private final String id;

    ReferenceTargetKind(String id) { this.id = id; }
    public String id() { return id; }
}
