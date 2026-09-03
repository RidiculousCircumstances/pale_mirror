package io.farfrontier.palemirror.frontier.v3.model;

/** Bounded post-controller behaviour; it never creates or deletes a body. */
public enum HiveCommandSignalPhase {
    CONNECTED,
    SIGNAL_MEMORY,
    INSTINCT,
    RECLAIMED;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
