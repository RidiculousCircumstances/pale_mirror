package io.farfrontier.palemirror.frontier;

import java.util.Objects;

/** A stable facility operation. It is domain state, not a visual animation cue. */
public final class FrontierOperation {
    public enum State { RUNNING, BLOCKED, DAMAGED }

    private final String id;
    private final String settlementId;
    private final String facilityId;
    private final FrontierOperationKind kind;
    private State state = State.RUNNING;
    private long revision;

    FrontierOperation(String id, String settlementId, String facilityId, FrontierOperationKind kind) {
        this.id = required(id, "id");
        this.settlementId = required(settlementId, "settlementId");
        this.facilityId = required(facilityId, "facilityId");
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public String id() { return id; }
    public String settlementId() { return settlementId; }
    public String facilityId() { return facilityId; }
    public FrontierOperationKind kind() { return kind; }
    public State state() { return state; }
    public long revision() { return revision; }
    public String materializationId() { return "frontier:operation:" + id; }

    boolean reconcile(FrontierFacility facility, boolean staffed) {
        State next = facility.state() == FrontierFacility.State.OPERATIONAL
                ? (staffed ? State.RUNNING : State.BLOCKED) : State.DAMAGED;
        if (state == next) return false;
        state = next;
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
