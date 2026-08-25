package io.farfrontier.palemirror.frontier.reference;

/** Exact typed form of Python {@code SectorControlState}. */
public enum ReferenceSectorControlState {
    HUMAN("human"),
    CONTESTED("contested"),
    HIVE("hive"),
    SCARRED("scarred"),
    ABANDONED("abandoned");

    private final String id;

    ReferenceSectorControlState(String id) { this.id = id; }

    public String id() { return id; }
}
