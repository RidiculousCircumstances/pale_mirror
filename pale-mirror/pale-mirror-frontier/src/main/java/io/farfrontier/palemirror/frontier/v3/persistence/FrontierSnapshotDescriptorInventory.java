package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;

/** Exact additive receipt upgrade; never a wildcard compatibility or schema fallback. */
final class FrontierSnapshotDescriptorInventory {
    // c56af5fd -> 8c6b758f adds only BakeryStationSceneReconciled to the existing
    // production descriptor. Aggregate bytes, historical events, limits and physical
    // lifecycle composition are unchanged. Preserve the world containing the diagnosed
    // stranded worker; ordinary checkpoint publication writes the new inventory.
    static final String BEFORE_STATION_RECOVERY = "5f2691f35f4efbb5b339eaa50745e945a54149126d8fd0e0b18962f33d595003";
    static final String WITH_STATION_RECOVERY = "b1cfb3bbfef60def85513d82b8ccd18283c5f858663e5ac4df0534a8c1c2e550";
    private FrontierSnapshotDescriptorInventory() { }

    static boolean accepts(String retained) {
        return accepts(retained, FrontierDurationProcessDriverRegistry.inventoryFingerprint());
    }

    static boolean accepts(String retained, String current) {
        return current.equals(retained)
                || WITH_STATION_RECOVERY.equals(current) && BEFORE_STATION_RECOVERY.equals(retained);
    }
}
