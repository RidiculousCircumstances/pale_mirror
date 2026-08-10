package io.farfrontier.palemirror.internal.combat;

import java.util.Objects;
import java.util.UUID;

/**
 * SavedData-owned mutable combat state for one already-registered PM actor.
 * Entity UUID and profile are verification data; neither establishes world
 * progression or scenario state.
 */
public final class ThreatActorControlState {
    public enum Status { ACTIVE, DEFEATED, RETIRED }

    private final String key;
    private final String sourceId;
    private final String facilityId;
    private final String role;
    private final String slotId;
    private String profileId;
    private UUID entityId;
    private int hitPoints;
    private long nextActionTick;
    private long nextMovementTick;
    private int routeCursor;
    private Status status;

    public ThreatActorControlState(String key, String sourceId, String facilityId, String role, String slotId,
                                   String profileId, UUID entityId, int hitPoints, long nextActionTick,
                                   long nextMovementTick, int routeCursor, Status status) {
        this.key = text(key, "key");
        this.sourceId = text(sourceId, "sourceId");
        this.facilityId = text(facilityId, "facilityId");
        this.role = text(role, "role");
        this.slotId = text(slotId, "slotId");
        this.profileId = text(profileId, "profileId");
        this.entityId = entityId;
        if (hitPoints < 0 || nextActionTick < 0 || nextMovementTick < 0 || routeCursor < 0) {
            throw new IllegalArgumentException("Invalid PM actor control state");
        }
        this.hitPoints = hitPoints;
        this.nextActionTick = nextActionTick;
        this.nextMovementTick = nextMovementTick;
        this.routeCursor = routeCursor;
        this.status = Objects.requireNonNull(status, "status");
    }

    public String key() { return key; }
    public String sourceId() { return sourceId; }
    public String facilityId() { return facilityId; }
    public String role() { return role; }
    public String slotId() { return slotId; }
    public String profileId() { return profileId; }
    public UUID entityId() { return entityId; }
    public int hitPoints() { return hitPoints; }
    public long nextActionTick() { return nextActionTick; }
    public long nextMovementTick() { return nextMovementTick; }
    public int routeCursor() { return routeCursor; }
    public Status status() { return status; }

    void attach(String profile, UUID entity, int health) {
        if (health < 1) throw new IllegalArgumentException("health must be positive");
        this.profileId = text(profile, "profileId");
        this.entityId = Objects.requireNonNull(entity, "entityId");
        this.hitPoints = health;
        this.nextActionTick = 0L;
        this.nextMovementTick = 0L;
        this.routeCursor = 0;
        this.status = Status.ACTIVE;
    }

    int applyDamage(int amount) {
        if (amount < 1) throw new IllegalArgumentException("damage must be positive");
        if (status != Status.ACTIVE) return hitPoints;
        hitPoints = Math.max(0, hitPoints - amount);
        if (hitPoints == 0) status = Status.DEFEATED;
        return hitPoints;
    }

    void scheduleAction(long gameTick) { nextActionTick = nonNegative(gameTick, "nextActionTick"); }
    void scheduleMovement(long gameTick, int cursor) {
        nextMovementTick = nonNegative(gameTick, "nextMovementTick");
        routeCursor = Math.max(0, cursor);
    }
    void retire() { status = Status.RETIRED; }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private static long nonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be non-negative");
        return value;
    }
}
