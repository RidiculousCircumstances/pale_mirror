package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/**
 * The bounded, identity-complete operator carrier for one retained incident.
 *
 * <p>This is a value projection of an incident, not an aggregate: it deliberately has no
 * transition methods and cannot supply an owner, type, or recovery decision.  Keeping the
 * causal coordinates beside the already stamped tuple makes an exported/rendered bundle
 * independently useful without introducing a second relationship graph.</p>
 */
public record DiagnosticIncidentBundle(String incidentId, DiagnosticTuple diagnostic, String eventId,
                                       String causeId, long revision, long instant, int occurrences,
                                       boolean awaitingReview, DiagnosticIncidentContext context) {
    public DiagnosticIncidentBundle {
        incidentId = require(incidentId, "incident bundle id");
        diagnostic = Objects.requireNonNull(diagnostic, "incident bundle diagnostic");
        eventId = require(eventId, "incident bundle event");
        causeId = require(causeId, "incident bundle cause");
        if (revision < 0 || instant < 0 || occurrences <= 0) throw new IllegalArgumentException("invalid incident bundle coordinates");
        if (awaitingReview != DiagnosticIncident.terminal(diagnostic.category())) throw new IllegalArgumentException("incident bundle disposition disagrees with tuple");
        context = Objects.requireNonNull(context, "incident bundle context");
    }
    private static String require(String value, String label) {
        value = Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " is blank");
        return value;
    }
}
