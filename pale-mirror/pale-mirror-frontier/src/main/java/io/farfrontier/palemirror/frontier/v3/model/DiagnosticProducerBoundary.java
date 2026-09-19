package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

/**
 * Last canonical admission boundary for named diagnostic outcomes.
 *
 * <p>It never chooses a tuple.  A new conflict/failure/recovery/quarantine
 * payload is rejected unless its producer has already supplied an exact tuple
 * that the closed extractor can expose.  Raw physical recovery transitions are
 * admitted earlier by the composed lifecycle capability, which stamps their
 * owner declaration before this reducer sees an event.</p>
 */
public final class DiagnosticProducerBoundary {
    private DiagnosticProducerBoundary() { }

    public static void requireAdmitted(FrontierPayload payload) {
        if (!namedDiagnosticOutcome(payload)) return;
        if (DiagnosticIncidentExtractor.tuple(payload).isEmpty()) {
            throw new IllegalArgumentException("named diagnostic outcome is not producer-stamped and registered: " + payload.type());
        }
    }

    static boolean namedDiagnosticOutcome(FrontierPayload payload) {
        String name = payload.getClass().getSimpleName();
        return !name.endsWith("TraversalBlocked") && (name.endsWith("Blocked") || name.endsWith("Failed")
                || name.endsWith("Conflicted") || name.endsWith("ConflictObserved")
                || name.endsWith("RecoveryUnresolved") || name.endsWith("QuarantineObserved"));
    }
}
