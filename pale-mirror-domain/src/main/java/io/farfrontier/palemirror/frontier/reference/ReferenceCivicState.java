package io.farfrontier.palemirror.frontier.reference;

/** Exact typed form of Python {@code CivicState}. */
public enum ReferenceCivicState {
    NORMAL("normal"),
    WATCH("watch"),
    EMERGENCY("emergency"),
    SIEGE("siege"),
    RECOVERY("recovery");

    private final String id;

    ReferenceCivicState(String id) { this.id = id; }

    public String id() { return id; }
}
