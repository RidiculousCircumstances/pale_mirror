package io.farfrontier.palemirror.frontier;

import java.util.Objects;

/** Functional facility, not a decorative block collection. */
public final class FrontierFacility {
    public enum State { OPERATIONAL, STARVED, DAMAGED, DESTROYED }

    private final String id;
    private final String settlementId;
    private final FrontierFacilityKind kind;
    private final FrontierPoint position;
    private State state = State.OPERATIONAL;
    private long revision;

    FrontierFacility(String id, String settlementId, FrontierFacilityKind kind, FrontierPoint position) {
        this.id = required(id, "id");
        this.settlementId = required(settlementId, "settlementId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.position = Objects.requireNonNull(position, "position");
    }

    public String id() { return id; }
    public String settlementId() { return settlementId; }
    public FrontierFacilityKind kind() { return kind; }
    public FrontierPoint position() { return position; }
    public State state() { return state; }
    public long revision() { return revision; }
    boolean damage() {
        if (state == State.DESTROYED) return false;
        state = State.DAMAGED;
        revision++;
        return true;
    }
    void restore(State state, long revision) {
        this.state = Objects.requireNonNull(state, "state");
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        this.revision = revision;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
