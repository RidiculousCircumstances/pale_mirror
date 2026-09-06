package io.farfrontier.palemirror.internal.effect;

/**
 * Durable owner of one physical-effect ledger.
 *
 * <p>The executor depends on this narrow persistence boundary rather than a
 * particular campaign SavedData document. This lets an isolated canonical
 * world use the same crash-safe effect protocol without creating a second
 * mutable owner for its actions.</p>
 */
public interface EffectLeaseStore {
    EffectLeaseLedger effectLeases();

    /** Marks the enclosing durable document dirty before or after a physical transition. */
    void markEffectLeaseDirty();
}
