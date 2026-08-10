package io.farfrontier.palemirror.internal.quarantine;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SavedData-owned record of legacy content that PM refuses to treat as managed state. */
public final class QuarantineLedger {
    private static final int MAX_RECORDS = 1024;
    private final Map<String, QuarantineRecord> records;

    public QuarantineLedger() { this(Map.of()); }

    public QuarantineLedger(Map<String, QuarantineRecord> records) {
        this.records = new LinkedHashMap<>();
        records.values().stream().sorted(Comparator.comparing(QuarantineRecord::id))
                .forEach(record -> this.records.put(record.id(), record));
    }

    public List<QuarantineRecord> records() { return List.copyOf(records.values()); }
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
        records.put(id, new QuarantineRecord(id, sourceId, kind, fingerprint, ownerId, gameTick, gameTick, 1, reason));
        compact();
        return true;
    }

    private void compact() {
        int overflow = records.size() - MAX_RECORDS;
        if (overflow <= 0) return;
        records.values().stream().sorted(Comparator.comparingLong(QuarantineRecord::lastSeenGameTick)
                        .thenComparing(QuarantineRecord::id)).limit(overflow).map(QuarantineRecord::id).toList()
                .forEach(records::remove);
    }
}
