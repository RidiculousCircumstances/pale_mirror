package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/**
 * Immutable, producer-derived diagnostic retention entry.  This is deliberately not an
 * aggregate or relationship view: the owner continues to retain and transition its state.
 */
public record DiagnosticIncident(String id, DiagnosticTuple diagnostic, String firstEventId,
                                String firstCauseId, long firstRevision, long firstInstant,
                                long lastRevision, long lastInstant, int occurrences,
                                boolean awaitingReview, DiagnosticIncidentContext context) {
    public DiagnosticIncident {
        id = require(id, "incident id"); diagnostic = Objects.requireNonNull(diagnostic, "incident diagnostic");
        firstEventId = require(firstEventId, "incident first event"); firstCauseId = require(firstCauseId, "incident first cause");
        if (firstRevision < 0 || firstInstant < 0 || lastRevision < firstRevision || lastInstant < firstInstant || occurrences <= 0) {
            throw new IllegalArgumentException("invalid incident retention coordinates");
        }
        if (awaitingReview != terminal(diagnostic.category())) throw new IllegalArgumentException("incident review disposition disagrees with diagnostic category");
        context = Objects.requireNonNull(context, "incident context");
    }
    public DiagnosticIncident(String id, DiagnosticTuple diagnostic, String firstEventId, String firstCauseId, long firstRevision, long firstInstant, long lastRevision, long lastInstant, int occurrences, boolean awaitingReview) {
        this(id, diagnostic, firstEventId, firstCauseId, firstRevision, firstInstant, lastRevision, lastInstant, occurrences, awaitingReview, DiagnosticIncidentContext.unavailable());
    }
    public static String idFor(DiagnosticTuple tuple) {
        Objects.requireNonNull(tuple, "incident tuple");
        return "diagnostic:" + tuple.reason().wireTag() + ":" + DiagnosticWireTags.subjectTag(tuple.subject().kind()) + ":" + tuple.subject().id().value();
    }
    public static boolean terminal(DiagnosticCategory category) {
        return category == DiagnosticCategory.RECONCILIATION_CONFLICT || category == DiagnosticCategory.RECOVERY_UNKNOWN
                || category == DiagnosticCategory.CANONICAL_INVARIANT_FAILURE || category == DiagnosticCategory.ADAPTER_OR_INFRASTRUCTURE_ERROR;
    }
    public DiagnosticIncident repeated(long revision, long instant) {
        if (revision < lastRevision || instant < lastInstant || occurrences == Integer.MAX_VALUE) throw new IllegalArgumentException("invalid incident repeat");
        return new DiagnosticIncident(id, diagnostic, firstEventId, firstCauseId, firstRevision, firstInstant, revision, instant, occurrences + 1, awaitingReview, context);
    }
    /** Automatic bundle export retains first causal identity while reporting the current occurrence count. */
    public DiagnosticIncidentBundle bundle() {
        return new DiagnosticIncidentBundle(id, diagnostic, firstEventId, firstCauseId, firstRevision, firstInstant, occurrences, awaitingReview, context);
    }
    private static String require(String value, String label) { value = Objects.requireNonNull(value, label); if (value.isBlank()) throw new IllegalArgumentException(label + " is blank"); return value; }
}
