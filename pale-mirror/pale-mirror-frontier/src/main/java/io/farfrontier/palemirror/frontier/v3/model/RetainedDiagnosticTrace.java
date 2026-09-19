package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;

import java.util.Objects;

/**
 * Bounded persisted canonical links for one diagnostic correlation.  It is an evidence view:
 * it cannot plan, select, or transition any owner.
 */
public record RetainedDiagnosticTrace(String correlation, String driver, String coldCommandId, String coldEventId,
                                      long coldRevision, long coldInstant, String observationId, String observationCommandId,
                                      String observationEventId, long observationRevision, long observationInstant) {
    public RetainedDiagnosticTrace {
        correlation = field(correlation); driver = field(driver); coldCommandId = field(coldCommandId); coldEventId = field(coldEventId);
        observationId = field(observationId); observationCommandId = field(observationCommandId); observationEventId = field(observationEventId);
        if (coldRevision < -1 || coldInstant < -1 || observationRevision < -1 || observationInstant < -1) throw new IllegalArgumentException("diagnostic trace coordinates");
    }
    public static RetainedDiagnosticTrace pending(ResourceSiteHarvestJob job, String driver) {
        return new RetainedDiagnosticTrace("resource-site-harvest:" + job.id().value(), driver, "not_captured", "not_captured", -1, -1,
                "not_observed", "not_captured", "not_captured", -1, -1);
    }
    public RetainedDiagnosticTrace cold(FrontierEvent event) {
        return new RetainedDiagnosticTrace(correlation, driver, command(event), event.id().value(), event.revision().value(), event.instant().ticks(),
                observationId, observationCommandId, observationEventId, observationRevision, observationInstant);
    }
    public RetainedDiagnosticTrace observed(PhysicalObservationId id, FrontierEvent event) {
        return new RetainedDiagnosticTrace(correlation, driver, coldCommandId, coldEventId, coldRevision, coldInstant, id.value(), command(event),
                event.id().value(), event.revision().value(), event.instant().ticks());
    }
    public boolean complete() { return !coldEventId.equals("not_captured") && !observationId.equals("not_observed") && !observationEventId.equals("not_captured"); }
    private static String command(FrontierEvent event) { return event.causes().commands().isEmpty() ? "root" : event.causes().commands().getLast().value(); }
    private static String field(String value) { value = Objects.requireNonNull(value, "diagnostic trace field"); if (value.isBlank() || value.length() > 512) throw new IllegalArgumentException("diagnostic trace field"); return value; }
}
