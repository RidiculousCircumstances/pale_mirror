package io.farfrontier.palemirror.api;

public enum ParcelKind {
    INFLUENCE(false),
    PUBLIC_INFRASTRUCTURE(true),
    COMMUNITY(true),
    PLAYER_LEASE(false),
    RESERVED(false);

    private final boolean pmManaged;
    ParcelKind(boolean pmManaged) { this.pmManaged = pmManaged; }
    public boolean pmManaged() { return pmManaged; }
}
