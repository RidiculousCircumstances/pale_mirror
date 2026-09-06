package io.farfrontier.palemirror.frontier;

import java.util.Objects;

/** One domain person maps to one managed Villager in the graybox profile. */
public final class FrontierResident {
    private final String id;
    private final String settlementId;
    private final FrontierResidentRole role;
    private final String materializationId;
    private boolean alive = true;
    private long revision;

    FrontierResident(String id, String settlementId, FrontierResidentRole role) {
        this.id = required(id, "id");
        this.settlementId = required(settlementId, "settlementId");
        this.role = Objects.requireNonNull(role, "role");
        this.materializationId = "frontier:resident:" + id;
    }

    public String id() { return id; }
    public String settlementId() { return settlementId; }
    public FrontierResidentRole role() { return role; }
    public String materializationId() { return materializationId; }
    public boolean alive() { return alive; }
    public long revision() { return revision; }

    boolean kill() {
        if (!alive) return false;
        alive = false;
        revision++;
        return true;
    }
    void restore(boolean alive, long revision) {
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        this.alive = alive;
        this.revision = revision;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
