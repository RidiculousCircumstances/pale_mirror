package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;

/** Current fresh-world composition only; no historical descriptor or schema fallback. */
final class FrontierSnapshotDescriptorInventory {
    private FrontierSnapshotDescriptorInventory() { }

    static boolean accepts(String retained) {
        return accepts(retained, FrontierDurationProcessDriverRegistry.inventoryFingerprint());
    }

    static boolean accepts(String retained, String current) {
        return current.equals(retained);
    }
}
