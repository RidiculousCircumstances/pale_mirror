package io.farfrontier.palemirror.frontier.reference;

/** One named type maps to one individually materialized zombie bioform. */
public enum ReferenceBioformKind {
    HARVESTER("harvester"),
    RAIDER("raider"),
    BREAKER("breaker"),
    SPORE_CARRIER("spore_carrier");

    private final String id;

    ReferenceBioformKind(String id) { this.id = id; }
    public String id() { return id; }
}
