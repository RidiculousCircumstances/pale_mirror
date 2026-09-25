package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded snapshot/WAL-backed typed lookup for already-stamped diagnostic facts.
 * It never selects a tuple, discovers an owner, or changes an owning aggregate's outcome.
 */
public record DiagnosticIncidentIndex(Map<String, DiagnosticIncident> incidents, Map<SubjectKey, String> bySubject,
                                      int droppedOptional) {
    public static final int MAX_INCIDENTS = 512;
    public static final int MAX_OPTIONAL = 128;
    public DiagnosticIncidentIndex {
        incidents = Map.copyOf(Objects.requireNonNull(incidents, "diagnostic incidents"));
        bySubject = Map.copyOf(Objects.requireNonNull(bySubject, "diagnostic subject index"));
        if (incidents.size() > MAX_INCIDENTS || droppedOptional < 0) throw new IllegalArgumentException("diagnostic incident retention limit exceeded");
        for (Map.Entry<SubjectKey, String> entry : bySubject.entrySet()) {
            DiagnosticIncident incident = incidents.get(entry.getValue());
            if (incident == null || !entry.getKey().matches(incident.diagnostic().subject())) throw new IllegalArgumentException("diagnostic subject index is not exact");
        }
    }
    public static DiagnosticIncidentIndex empty() { return new DiagnosticIncidentIndex(Map.of(), Map.of(), 0); }
    public Optional<DiagnosticIncident> incident(String id) { return Optional.ofNullable(incidents.get(id)); }
    /** Exact, automatic bundle lookup.  There is exactly one bundle for one retained incident identity. */
    public Optional<DiagnosticIncidentBundle> bundle(String id) { return incident(id).map(DiagnosticIncident::bundle); }
    /** Latest retained observation for a subject, not exclusive ownership of that subject. */
    public Optional<DiagnosticIncident> why(DiagnosticSubject subject) { return Optional.ofNullable(bySubject.get(SubjectKey.of(subject))).map(incidents::get); }
    public DiagnosticIncidentIndex retain(DiagnosticTuple tuple, String eventId, String causeId, long revision, long instant) {
        return retain(DiagnosticIncident.idFor(tuple), tuple, eventId, causeId, revision, instant, DiagnosticIncidentContext.unavailable());
    }
    public DiagnosticIncidentIndex retain(DiagnosticTuple tuple, String eventId, String causeId, long revision, long instant, DiagnosticIncidentContext context) {
        return retain(DiagnosticIncident.idFor(tuple), tuple, eventId, causeId, revision, instant, context);
    }
    public DiagnosticIncidentIndex retain(String id, DiagnosticTuple tuple, String eventId, String causeId, long revision, long instant) {
        return retain(id, tuple, eventId, causeId, revision, instant, DiagnosticIncidentContext.unavailable());
    }
    public DiagnosticIncidentIndex retain(String id, DiagnosticTuple tuple, String eventId, String causeId, long revision, long instant, DiagnosticIncidentContext context) {
        Objects.requireNonNull(tuple, "producer tuple"); Objects.requireNonNull(context, "incident context");
        id = Objects.requireNonNull(id, "producer incident id");
        DiagnosticIncident existing = incidents.get(id);
        if (existing != null) {
            if (!existing.diagnostic().equals(tuple)) throw new IllegalArgumentException("same diagnostic incident identity has a different tuple");
            Map<String, DiagnosticIncident> next = new LinkedHashMap<>(incidents); next.put(id, existing.repeated(revision, instant));
            return new DiagnosticIncidentIndex(next, subjectIndex(next), droppedOptional);
        }
        // A new terminal account must be admitted or reject its canonical transaction. Optional
        // nonterminal detail degrades visibly; it can never make a terminal summary green.
        if (!DiagnosticIncident.terminal(tuple.category()) && optionalCount() >= MAX_OPTIONAL) {
            return new DiagnosticIncidentIndex(incidents, bySubject, Math.addExact(droppedOptional, 1));
        }
        if (incidents.size() >= MAX_INCIDENTS) {
            return compactOneOptional().retain(id, tuple, eventId, causeId, revision, instant, context);
        }
        DiagnosticIncident created = new DiagnosticIncident(id, tuple, eventId, causeId, revision, instant, revision, instant, 1,
                DiagnosticIncident.terminal(tuple.category()), context);
        Map<String, DiagnosticIncident> next = new LinkedHashMap<>(incidents); next.put(id, created);
        return new DiagnosticIncidentIndex(next, subjectIndex(next), droppedOptional);
    }
    private int optionalCount() { return (int) incidents.values().stream().filter(value -> !value.awaitingReview()).count(); }
    /** Closed nonterminal detail is compacted by retained logical coordinates, never by map iteration. */
    private DiagnosticIncidentIndex compactOneOptional() {
        DiagnosticIncident evicted = incidents.values().stream().filter(value -> !value.awaitingReview())
                .min(java.util.Comparator.comparingLong(DiagnosticIncident::lastRevision).thenComparingLong(DiagnosticIncident::lastInstant).thenComparing(DiagnosticIncident::id))
                .orElseThrow(() -> new IllegalStateException("diagnostic optional compaction has no evictable detail"));
        Map<String, DiagnosticIncident> next = new LinkedHashMap<>(incidents); next.remove(evicted.id());
        return new DiagnosticIncidentIndex(next, subjectIndex(next), Math.addExact(droppedOptional, 1));
    }
    private static Map<SubjectKey, String> subjectIndex(Map<String, DiagnosticIncident> incidents) {
        Map<SubjectKey, String> result = new LinkedHashMap<>();
        incidents.values().stream().sorted(java.util.Comparator.comparingLong(DiagnosticIncident::lastRevision)
                .thenComparingLong(DiagnosticIncident::lastInstant).thenComparing(DiagnosticIncident::id))
                .forEach(value -> result.put(SubjectKey.of(value.diagnostic().subject()), value.id()));
        return result;
    }
    public record SubjectKey(DiagnosticSubjectKind kind, SubjectId id) {
        public SubjectKey { kind = Objects.requireNonNull(kind, "diagnostic subject key kind"); id = Objects.requireNonNull(id, "diagnostic subject key id"); }
        static SubjectKey of(DiagnosticSubject subject) { return new SubjectKey(subject.kind(), subject.id()); }
        boolean matches(DiagnosticSubject subject) { return kind == subject.kind() && id.equals(subject.id()); }
    }
}
