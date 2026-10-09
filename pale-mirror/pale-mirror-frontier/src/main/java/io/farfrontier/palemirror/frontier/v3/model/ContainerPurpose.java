package io.farfrontier.palemirror.frontier.v3.model;

/** Producer-declared physical storage scope. No ID or runtime stock supplies this identity. */
public enum ContainerPurpose {
    UNSCOPED(1, null),
    SETTLEMENT_DEPOT(2, "container.settlement-depot"),
    HIVE_STORE(3, "container.hive-store"),
    PRODUCTION_STATION(4, "container.production-station"),
    MOBILE_STORAGE(5, "container.mobile-storage"),
    EXTRACTIVE_STORAGE(6, "container.extractive-storage"),
    ROUTE_MAINTENANCE(7, null);

    private final int wireTag;
    private final String referenceKind;
    ContainerPurpose(int wireTag, String referenceKind) { this.wireTag = wireTag; this.referenceKind = referenceKind; }
    public int wireTag() { return wireTag; }
    public boolean referenceScope() { return referenceKind != null; }
    public String referenceKind() {
        if (!referenceScope()) throw new IllegalArgumentException("container has no declared reference scope");
        return referenceKind;
    }
    public static ContainerPurpose fromWireTag(int tag) {
        return switch (tag) {
            case 1 -> UNSCOPED; case 2 -> SETTLEMENT_DEPOT; case 3 -> HIVE_STORE;
            case 4 -> PRODUCTION_STATION; case 5 -> MOBILE_STORAGE; case 6 -> EXTRACTIVE_STORAGE;
            case 7 -> ROUTE_MAINTENANCE;
            default -> throw new IllegalArgumentException("unknown container purpose tag: " + tag);
        };
    }
}
