package io.farfrontier.palemirror.internal.quarantine;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticIncident;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticIncidentBundle;
import io.farfrontier.palemirror.frontier.v3.model.DiagnosticSubject;
import java.util.Optional;

/** SavedData-owned record of legacy content that PM refuses to treat as managed state. */
public final class QuarantineLedger {
    /** Quarantine records are terminal invariant facts and may never be compacted. */
    public static final int MAX_RECORDS = 1024;
    private final Map<String, QuarantineRecord> records;

    public QuarantineLedger() { this(Map.of()); }

    public QuarantineLedger(Map<String, QuarantineRecord> records) {
        this.records = new LinkedHashMap<>();
        records.values().stream().sorted(Comparator.comparing(QuarantineRecord::id))
                .forEach(record -> this.records.put(record.id(), record));
    }

    public List<QuarantineRecord> records() { return List.copyOf(records.values()); }
    /** Direct typed lookup over the SavedData-owned terminal records; no candidate scan. */
    public Optional<DiagnosticIncidentBundle> why(DiagnosticSubject subject) {
        return Optional.ofNullable(records.get(subject.id().value())).filter(record -> record.cause().subject().equals(subject))
                .map(this::bundle);
    }
    public Optional<DiagnosticIncidentBundle> incident(String id) { return Optional.ofNullable(records.get(id)).map(this::bundle); }
    public void clear() { records.clear(); }

    /** Returns true only when this is a new diagnostic record. */
    public boolean observe(String sourceId, QuarantineKind kind, String fingerprint, UUID ownerId, long gameTick, String reason) {
        String identity = sourceId + "|" + kind + "|" + fingerprint + "|" + (ownerId == null ? "" : ownerId);
        String id = "quarantine:" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        QuarantineRecord existing = records.get(id);
        if (existing != null) {
            existing.observe(gameTick, reason);
            return false;
        }
        if (records.size() >= MAX_RECORDS) throw new IllegalStateException("quarantine diagnostic admission exhausted for required terminal fact");
        records.put(id, new QuarantineRecord(id, sourceId, kind, fingerprint, ownerId, gameTick, gameTick, 1, reason,
                QuarantineDiagnosticProducer.stamp(id)));
        return true;
    }
    private DiagnosticIncidentBundle bundle(QuarantineRecord record) {
        return new DiagnosticIncident(record.id(), record.cause(), record.id(), "quarantine:" + record.sourceId(),
                record.firstSeenGameTick(), record.firstSeenGameTick(), record.lastSeenGameTick(), record.lastSeenGameTick(),
                record.observations(), true).bundle();
    }
}
