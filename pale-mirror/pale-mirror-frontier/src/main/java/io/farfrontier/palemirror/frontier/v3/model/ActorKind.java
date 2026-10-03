package io.farfrontier.palemirror.frontier.v3.model;

/** Declared by resident/bioform creation, never discovered from an ID, job or physical class. */
public enum ActorKind {
    RESIDENT(0), BIOFORM(1);
    private final int wireTag;
    ActorKind(int wireTag) { this.wireTag = wireTag; }
    public int wireTag() { return wireTag; }
}
