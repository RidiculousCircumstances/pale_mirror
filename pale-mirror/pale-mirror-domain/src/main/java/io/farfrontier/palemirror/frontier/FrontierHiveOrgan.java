package io.farfrontier.palemirror.frontier;

import java.util.Locale;
import java.util.Objects;

/** One visible, destructible graybox hive organ. */
public final class FrontierHiveOrgan {
    public enum State { ALIVE, DESTROYED }

    private final String id;
    private final String hiveId;
    private final FrontierHiveOrganKind kind;
    private final FrontierPoint position;
    private final long bornDay;
    private State state = State.ALIVE;
    private long revision;

    FrontierHiveOrgan(String id, String hiveId, FrontierHiveOrganKind kind, FrontierPoint position) {
        this(id, hiveId, kind, position, 0);
    }
    FrontierHiveOrgan(String id, String hiveId, FrontierHiveOrganKind kind, FrontierPoint position, long bornDay) {
        if (id == null || id.isBlank() || hiveId == null || hiveId.isBlank()) throw new IllegalArgumentException("invalid hive organ identity");
        if (bornDay < 0) throw new IllegalArgumentException("bornDay must not be negative");
        this.id = id;
        this.hiveId = hiveId;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.position = Objects.requireNonNull(position, "position");
        this.bornDay = bornDay;
    }
    public String id() { return id; }
    public String hiveId() { return hiveId; }
    public FrontierHiveOrganKind kind() { return kind; }
    public FrontierPoint position() { return position; }
    public long bornDay() { return bornDay; }
    public State state() { return state; }
    public long revision() { return revision; }
    public String materializationId() { return "frontier:hive-organ:" + id; }
    boolean destroy() {
        if (state == State.DESTROYED) return false;
        state = State.DESTROYED;
        revision++;
        return true;
    }
    void restore(State state, long revision) {
        this.state = Objects.requireNonNull(state, "state");
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        this.revision = revision;
    }
    static String dynamicIdFor(String hiveId, FrontierHiveOrganKind kind, FrontierPoint position, long bornDay) {
        if (hiveId == null || hiveId.isBlank() || kind == null || position == null || bornDay < 1) {
            throw new IllegalArgumentException("invalid dynamic hive organ identity");
        }
        return hiveId + ":organ:" + kind.name().toLowerCase(Locale.ROOT) + ":born:" + bornDay
                + ":x:" + position.x() + ":z:" + position.z();
    }
}
