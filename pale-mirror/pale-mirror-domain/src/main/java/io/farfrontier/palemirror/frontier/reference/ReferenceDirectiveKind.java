package io.farfrontier.palemirror.frontier.reference;

/** Exact Python {@code DirectiveKind} vocabulary. */
public enum ReferenceDirectiveKind {
    PRIORITIZE("prioritize"), AVOID("avoid"), RECON("recon"), RAID("raid"), EVACUATE("evacuate"), DEFEND("defend");

    private final String id;

    ReferenceDirectiveKind(String id) { this.id = id; }
    public String id() { return id; }
}
