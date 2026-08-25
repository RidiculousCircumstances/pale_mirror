package io.farfrontier.palemirror.frontier.reference;

/** Exact Python {@code FieldLinkKind} vocabulary. */
public enum ReferenceFieldLinkKind {
    SUPPLY_CORRIDOR("supply_corridor"),
    FORTIFIED_LINE("fortified_line");

    private final String id;

    ReferenceFieldLinkKind(String id) { this.id = id; }
    public String id() { return id; }
}
