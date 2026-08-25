package io.farfrontier.palemirror.frontier.reference;

/** Exact Python {@code FieldPostStatus} lifecycle. */
public enum ReferenceFieldPostStatus {
    BUILDING("building"),
    ACTIVE("active"),
    ISOLATED("isolated"),
    ABANDONED("abandoned"),
    OVERRUN("overrun"),
    DISMANTLED("dismantled");

    private final String id;

    ReferenceFieldPostStatus(String id) { this.id = id; }
    public String id() { return id; }
}
