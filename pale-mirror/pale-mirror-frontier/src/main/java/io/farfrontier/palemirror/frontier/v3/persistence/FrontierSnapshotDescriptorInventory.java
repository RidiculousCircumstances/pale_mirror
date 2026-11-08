package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;

/** Exact composition fence, including one schema351 wire-compatible producer
 * declaration correction. No payload, owner, wire tag or physical capability changed. */
final class FrontierSnapshotDescriptorInventory {
    static final String BEFORE_CLINICAL_INTERRUPTION = "0846f5232278dcd5407b5b66290f998526468d8fdf20d0cdab5b994b0b4646fe";
    static final String WITH_CLINICAL_INTERRUPTION = "a4e05dd9504bb3b85a31c8400e0c0aa7c3b5530762b230f0857aaa8043fa64ca";
    private FrontierSnapshotDescriptorInventory() { }

    static boolean accepts(String retained) {
        return accepts(retained, FrontierDurationProcessDriverRegistry.inventoryFingerprint());
    }

    static boolean accepts(String retained, String current) {
        return current.equals(retained)
                || WITH_CLINICAL_INTERRUPTION.equals(current) && BEFORE_CLINICAL_INTERRUPTION.equals(retained);
    }
}
