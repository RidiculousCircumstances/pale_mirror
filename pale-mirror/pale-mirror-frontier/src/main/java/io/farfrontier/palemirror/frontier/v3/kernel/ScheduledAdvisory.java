package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import java.util.Optional;

/** Explicit owner capability for disposable snapshot advice, never precomputed mutations.
 * While it runs, independent canonical work may proceed. Only its owner may validate
 * dependencies and supply a fresh exact-predecessor preparation for publication. */
@FunctionalInterface
public interface ScheduledAdvisory<S> {
    Completion<S> prepare();
    @FunctionalInterface
    interface Completion<S> {
        /** Empty rejects obsolete advice without consuming the retained due action. */
        Optional<ScheduledPreparation> revalidate(S current, SimInstant executionAt);
        /** Bounded advisory telemetry only; never consulted by admission or policy. */
        default String rejectionReason() { return "UNSPECIFIED"; }
    }
}
