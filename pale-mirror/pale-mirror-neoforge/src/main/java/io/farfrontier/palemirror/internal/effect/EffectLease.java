package io.farfrontier.palemirror.internal.effect;

import java.util.Objects;
import java.util.UUID;

/**
 * Persisted permission to perform one bounded physical effect.
 *
 * <p>The record deliberately holds no domain outcome.  It only proves that a
 * particular adapter action was authorized for a PM-owned source/site/actor
 * and makes a crash during that action visible instead of replaying it.</p>
 */
public final class EffectLease {
    private static final int MAX_RECEIPT_LENGTH = 512;
    private final String id;
    private final String idempotencyKey;
    private final String sourceId;
    private final String facilityId;
    private final String actorSlotId;
    private final String kind;
    private final long createdAtGameTick;
    private final long expiresAtGameTick;
    private EffectLeaseState state;
    private long finishedAtGameTick;
    private UUID nativeReference;
    private String diagnostic;
    private String receipt;

    public EffectLease(String id, String idempotencyKey, String sourceId, String facilityId, String actorSlotId,
                       String kind, long createdAtGameTick, long expiresAtGameTick, EffectLeaseState state,
                       long finishedAtGameTick, UUID nativeReference, String diagnostic) {
        this(id, idempotencyKey, sourceId, facilityId, actorSlotId, kind, createdAtGameTick, expiresAtGameTick,
                state, finishedAtGameTick, nativeReference, diagnostic, "");
    }

    public EffectLease(String id, String idempotencyKey, String sourceId, String facilityId, String actorSlotId,
                       String kind, long createdAtGameTick, long expiresAtGameTick, EffectLeaseState state,
                       long finishedAtGameTick, UUID nativeReference, String diagnostic, String receipt) {
        this.id = requireText(id, "id");
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        this.sourceId = requireText(sourceId, "sourceId");
        this.facilityId = requireText(facilityId, "facilityId");
        this.actorSlotId = actorSlotId == null ? "" : actorSlotId;
        this.kind = requireText(kind, "kind");
        if (createdAtGameTick < 0 || expiresAtGameTick < createdAtGameTick) {
            throw new IllegalArgumentException("Effect lease ticks are invalid");
        }
        this.createdAtGameTick = createdAtGameTick;
        this.expiresAtGameTick = expiresAtGameTick;
        this.state = Objects.requireNonNull(state, "state");
        this.finishedAtGameTick = finishedAtGameTick;
        this.nativeReference = nativeReference;
        this.diagnostic = diagnostic == null ? "" : diagnostic;
        this.receipt = boundedReceipt(receipt);
    }

    public static EffectLease planned(String id, String idempotencyKey, String sourceId, String facilityId,
                                      String actorSlotId, String kind, long createdAtGameTick, long expiresAtGameTick) {
        return new EffectLease(id, idempotencyKey, sourceId, facilityId, actorSlotId, kind, createdAtGameTick,
                expiresAtGameTick, EffectLeaseState.PLANNED, 0L, null, "");
    }

    public String id() { return id; }
    public String idempotencyKey() { return idempotencyKey; }
    public String sourceId() { return sourceId; }
    public String facilityId() { return facilityId; }
    public String actorSlotId() { return actorSlotId; }
    public String kind() { return kind; }
    public long createdAtGameTick() { return createdAtGameTick; }
    public long expiresAtGameTick() { return expiresAtGameTick; }
    public EffectLeaseState state() { return state; }
    public long finishedAtGameTick() { return finishedAtGameTick; }
    public UUID nativeReference() { return nativeReference; }
    public String diagnostic() { return diagnostic; }
    public String receipt() { return receipt; }

    boolean begin() {
        if (state != EffectLeaseState.PLANNED) return false;
        state = EffectLeaseState.RUNNING;
        return true;
    }

    void complete(long gameTick) {
        complete(gameTick, "");
    }

    void complete(long gameTick, String physicalReceipt) {
        if (state != EffectLeaseState.RUNNING) throw new IllegalStateException("Cannot complete effect lease " + id + " from " + state);
        state = EffectLeaseState.COMPLETED;
        finishedAtGameTick = gameTick;
        diagnostic = "";
        receipt = boundedReceipt(physicalReceipt);
    }

    void fail(long gameTick, String failure) {
        if (state.terminal()) return;
        state = EffectLeaseState.FAILED;
        finishedAtGameTick = gameTick;
        diagnostic = failure == null ? "effect executor failed" : failure;
        receipt = "";
    }

    void markUnknownAfterRestart(long gameTick) {
        if (state != EffectLeaseState.RUNNING) return;
        state = EffectLeaseState.UNKNOWN_AFTER_RESTART;
        finishedAtGameTick = gameTick;
        diagnostic = "Effect was running when the server stopped; it is not replayed automatically";
        receipt = "";
    }

    void expire(long gameTick) {
        if (state.terminal()) return;
        state = EffectLeaseState.EXPIRED;
        finishedAtGameTick = gameTick;
        diagnostic = "Effect lease expired before execution";
        receipt = "";
    }

    void setNativeReference(UUID value) { nativeReference = value; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    private static String boundedReceipt(String value) {
        String result = value == null ? "" : value;
        if (result.length() > MAX_RECEIPT_LENGTH) throw new IllegalArgumentException("effect receipt exceeds its bound");
        return result;
    }
}
