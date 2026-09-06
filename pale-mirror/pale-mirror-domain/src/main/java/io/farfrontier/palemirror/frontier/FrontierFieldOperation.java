package io.farfrontier.palemirror.frontier;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A settlement-owned, supplied field response. Residents retain their own identities so physical
 * Villager deaths change the operation's real available force instead of a cosmetic headcount.
 */
public final class FrontierFieldOperation {
    public enum Kind { DEFEND }
    public enum State { ASSEMBLING, EN_ROUTE, ON_STATION, RETURNING, COMPLETED, ABORTED }

    private final String id;
    private final String settlementId;
    private final String targetAssaultId;
    private final Kind kind;
    private final List<String> participantIds;
    private final long startedDay;
    private final int transitDays;
    private FrontierPoint position;
    private State state = State.ASSEMBLING;
    private int transitProgress;
    private int stationDays;
    private long finishedDay = -1;
    private long revision;

    FrontierFieldOperation(String id, String settlementId, String targetAssaultId, Kind kind,
                           List<String> participantIds, long startedDay, int transitDays, FrontierPoint origin) {
        this.id = required(id, "id");
        this.settlementId = required(settlementId, "settlementId");
        this.targetAssaultId = required(targetAssaultId, "targetAssaultId");
        this.kind = Objects.requireNonNull(kind, "kind");
        if (participantIds == null || participantIds.isEmpty() || participantIds.size() > 6
                || new LinkedHashSet<>(participantIds).size() != participantIds.size()) {
            throw new IllegalArgumentException("field participants must be unique and bounded");
        }
        this.participantIds = List.copyOf(participantIds);
        if (startedDay < 1 || transitDays < 1) throw new IllegalArgumentException("invalid field operation schedule");
        this.startedDay = startedDay;
        this.transitDays = transitDays;
        this.position = Objects.requireNonNull(origin, "origin");
    }

    public String id() { return id; }
    public String settlementId() { return settlementId; }
    public String targetAssaultId() { return targetAssaultId; }
    public Kind kind() { return kind; }
    public List<String> participantIds() { return participantIds; }
    public long startedDay() { return startedDay; }
    public int transitDays() { return transitDays; }
    public FrontierPoint position() { return position; }
    public State state() { return state; }
    public int transitProgress() { return transitProgress; }
    public int stationDays() { return stationDays; }
    public long finishedDay() { return finishedDay; }
    public long revision() { return revision; }
    public String materializationId() { return "frontier:field-operation:" + id; }
    public boolean terminal() { return state == State.COMPLETED || state == State.ABORTED; }

    boolean depart() { return transition(State.ASSEMBLING, State.EN_ROUTE); }
    boolean advanceOutbound(FrontierPoint origin, FrontierPoint target) {
        if (state != State.EN_ROUTE) return false;
        transitProgress = Math.min(transitDays, transitProgress + 1);
        position = interpolate(origin, target, transitProgress, transitDays);
        if (transitProgress == transitDays) state = State.ON_STATION;
        revision++;
        return true;
    }
    boolean stationDay() {
        if (state != State.ON_STATION) return false;
        stationDays++;
        revision++;
        return true;
    }
    boolean beginReturn() {
        if (state != State.ASSEMBLING && state != State.EN_ROUTE && state != State.ON_STATION) return false;
        state = State.RETURNING;
        transitProgress = 0;
        revision++;
        return true;
    }
    boolean advanceReturn(FrontierPoint target, FrontierPoint origin, long day) {
        if (state != State.RETURNING) return false;
        transitProgress = Math.min(transitDays, transitProgress + 1);
        position = interpolate(target, origin, transitProgress, transitDays);
        if (transitProgress == transitDays) { state = State.COMPLETED; finishedDay = day; }
        revision++;
        return true;
    }
    boolean abort(long day) {
        if (terminal()) return false;
        state = State.ABORTED;
        finishedDay = day;
        revision++;
        return true;
    }
    void restore(State state, FrontierPoint position, int transitProgress, int stationDays, long finishedDay, long revision) {
        this.state = Objects.requireNonNull(state, "state");
        this.position = Objects.requireNonNull(position, "position");
        if (transitProgress < 0 || transitProgress > transitDays || stationDays < 0 || revision < 0
                || (terminal() && finishedDay < startedDay) || (!terminal() && finishedDay != -1)) {
            throw new IllegalArgumentException("invalid persisted field operation state");
        }
        this.transitProgress = transitProgress;
        this.stationDays = stationDays;
        this.finishedDay = finishedDay;
        this.revision = revision;
    }
    static String idFor(String settlementId, String assaultId, long startedDay) {
        return "frontier:field:" + settlementId.substring(settlementId.lastIndexOf(':') + 1) + ":"
                + assaultId.replace(':', '_') + ":day:" + String.format(Locale.ROOT, "%08d", startedDay);
    }
    static int travelDays(FrontierPoint origin, FrontierPoint target) {
        return FrontierBalance.humanTravelDays(origin, target);
    }
    private boolean transition(State expected, State next) {
        if (state != expected) return false;
        state = next;
        revision++;
        return true;
    }
    private static FrontierPoint interpolate(FrontierPoint origin, FrontierPoint target, int progress, int total) {
        return new FrontierPoint(origin.x() + Math.round((target.x() - origin.x()) * progress / (float) total),
                origin.z() + Math.round((target.z() - origin.z()) * progress / (float) total));
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
