package io.farfrontier.palemirror.internal.quarantine;

import java.util.Objects;
import java.util.UUID;

/** Compact diagnostic record for a legacy object PM intentionally does not adopt. */
public final class QuarantineRecord {
    private final String id;
    private final String sourceId;
    private final QuarantineKind kind;
    private final String fingerprint;
    private final UUID ownerId;
    private final long firstSeenGameTick;
    private long lastSeenGameTick;
    private int observations;
    private String diagnostic;

    public QuarantineRecord(String id, String sourceId, QuarantineKind kind, String fingerprint, UUID ownerId,
                            long firstSeenGameTick, long lastSeenGameTick, int observations, String diagnostic) {
        this.id = requireText(id, "id");
        this.sourceId = requireText(sourceId, "sourceId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.fingerprint = requireText(fingerprint, "fingerprint");
        this.ownerId = ownerId;
        this.firstSeenGameTick = firstSeenGameTick;
        this.lastSeenGameTick = lastSeenGameTick;
        this.observations = Math.max(1, observations);
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public String id() { return id; }
    public String sourceId() { return sourceId; }
    public QuarantineKind kind() { return kind; }
    public String fingerprint() { return fingerprint; }
    public UUID ownerId() { return ownerId; }
    public long firstSeenGameTick() { return firstSeenGameTick; }
    public long lastSeenGameTick() { return lastSeenGameTick; }
    public int observations() { return observations; }
    public String diagnostic() { return diagnostic; }

    void observe(long gameTick, String reason) {
        lastSeenGameTick = Math.max(lastSeenGameTick, gameTick);
        observations = Math.min(Integer.MAX_VALUE, observations + 1);
        if (reason != null && !reason.isBlank()) diagnostic = reason;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
