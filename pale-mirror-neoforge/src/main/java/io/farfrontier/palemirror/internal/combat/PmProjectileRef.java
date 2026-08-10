package io.farfrontier.palemirror.internal.combat;

import java.util.Objects;
import java.util.UUID;

/** Persisted provenance and recovery state for a short-lived PM projectile. */
public final class PmProjectileRef {
    public enum State { PLANNED, ACTIVE, IMPACTED, DISCARDED, UNKNOWN_AFTER_RESTART, EXPIRED }

    private final String id;
    private final String sourceId;
    private final String facilityId;
    private final String shooterKey;
    private final UUID targetId;
    private final String visualProfileId;
    private final String launchLeaseId;
    private final float damage;
    private final long createdAtGameTick;
    private final long expiresAtGameTick;
    private UUID entityId;
    private String impactLeaseId;
    private State state;
    private long finishedAtGameTick;
    private String diagnostic;

    public PmProjectileRef(String id, String sourceId, String facilityId, String shooterKey, UUID targetId, String visualProfileId,
                           String launchLeaseId, float damage, long createdAtGameTick, long expiresAtGameTick, UUID entityId,
                           String impactLeaseId, State state, long finishedAtGameTick, String diagnostic) {
        this.id = text(id, "id");
        this.sourceId = text(sourceId, "sourceId");
        this.facilityId = text(facilityId, "facilityId");
        this.shooterKey = text(shooterKey, "shooterKey");
        this.targetId = targetId;
        this.visualProfileId = text(visualProfileId, "visualProfileId");
        this.launchLeaseId = text(launchLeaseId, "launchLeaseId");
        if (damage <= 0.0F) throw new IllegalArgumentException("damage must be positive");
        this.damage = damage;
        if (createdAtGameTick < 0 || expiresAtGameTick < createdAtGameTick || finishedAtGameTick < 0) {
            throw new IllegalArgumentException("Invalid PM projectile ticks");
        }
        this.createdAtGameTick = createdAtGameTick;
        this.expiresAtGameTick = expiresAtGameTick;
        this.entityId = entityId;
        this.impactLeaseId = impactLeaseId == null ? "" : impactLeaseId;
        this.state = Objects.requireNonNull(state, "state");
        this.finishedAtGameTick = finishedAtGameTick;
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public String id() { return id; }
    public String sourceId() { return sourceId; }
    public String facilityId() { return facilityId; }
    public String shooterKey() { return shooterKey; }
    /** The only living entity that this PM projectile may damage. Null means legacy/safe-no-damage. */
    public UUID targetId() { return targetId; }
    public String visualProfileId() { return visualProfileId; }
    public String launchLeaseId() { return launchLeaseId; }
    public float damage() { return damage; }
    public long createdAtGameTick() { return createdAtGameTick; }
    public long expiresAtGameTick() { return expiresAtGameTick; }
    public UUID entityId() { return entityId; }
    public String impactLeaseId() { return impactLeaseId; }
    public State state() { return state; }
    public long finishedAtGameTick() { return finishedAtGameTick; }
    public String diagnostic() { return diagnostic; }

    void activate(UUID nativeEntity) {
        if (state != State.PLANNED) return;
        entityId = Objects.requireNonNull(nativeEntity, "entityId");
        state = State.ACTIVE;
    }

    boolean impact(String leaseId, long gameTick) {
        if (state != State.ACTIVE) return false;
        impactLeaseId = text(leaseId, "impactLeaseId");
        state = State.IMPACTED;
        finishedAtGameTick = gameTick;
        diagnostic = "";
        return true;
    }

    void discard(long gameTick, String reason) {
        if (state == State.IMPACTED || state == State.DISCARDED || state == State.EXPIRED) return;
        state = State.DISCARDED;
        finishedAtGameTick = gameTick;
        diagnostic = reason == null ? "discarded" : reason;
    }

    void recoverAfterRestart(long gameTick) {
        if (state != State.ACTIVE && state != State.PLANNED) return;
        state = State.UNKNOWN_AFTER_RESTART;
        finishedAtGameTick = gameTick;
        diagnostic = "Projectile state was active during restart; it is never replayed implicitly";
    }

    void expire(long gameTick) {
        if (state == State.IMPACTED || state == State.DISCARDED || state == State.EXPIRED) return;
        state = State.EXPIRED;
        finishedAtGameTick = gameTick;
        diagnostic = "Projectile expired without an approved impact";
    }

    boolean terminal() { return state == State.IMPACTED || state == State.DISCARDED || state == State.EXPIRED || state == State.UNKNOWN_AFTER_RESTART; }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
