package io.farfrontier.palemirror.frontier;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A bounded hive field operation. It owns the strategic lifecycle; individual bioforms retain
 * their own identities so a physical zombie death can still cancel this operation correctly.
 */
public final class FrontierAssault {
    public enum Kind { RAID }
    public enum State { ASSEMBLING, EN_ROUTE, ENGAGING, RETURNING, COMPLETED, ABORTED }

    private final String id;
    private final String hiveId;
    private final String targetSettlementId;
    private final Kind kind;
    private final List<String> participantIds;
    private final long startedDay;
    private final int transitDays;
    private FrontierPoint position;
    private State state = State.ASSEMBLING;
    private int transitProgress;
    private int engagementDays;
    private long finishedDay = -1;
    private long revision;

    FrontierAssault(String id, String hiveId, String targetSettlementId, Kind kind,
                    List<String> participantIds, long startedDay, int transitDays, FrontierPoint origin) {
        this.id = required(id, "id");
        this.hiveId = required(hiveId, "hiveId");
        this.targetSettlementId = required(targetSettlementId, "targetSettlementId");
        this.kind = Objects.requireNonNull(kind, "kind");
        if (participantIds == null || participantIds.isEmpty() || participantIds.size() > 12
                || new LinkedHashSet<>(participantIds).size() != participantIds.size()) {
            throw new IllegalArgumentException("assault participants must be unique and bounded");
        }
        this.participantIds = List.copyOf(participantIds);
        if (startedDay < 1 || transitDays < 1) throw new IllegalArgumentException("invalid assault schedule");
        this.startedDay = startedDay;
        this.transitDays = transitDays;
        this.position = Objects.requireNonNull(origin, "origin");
    }

    public String id() { return id; }
    public String hiveId() { return hiveId; }
    public String targetSettlementId() { return targetSettlementId; }
    public Kind kind() { return kind; }
    public List<String> participantIds() { return participantIds; }
    public long startedDay() { return startedDay; }
    public int transitDays() { return transitDays; }
    public FrontierPoint position() { return position; }
    public State state() { return state; }
    public int transitProgress() { return transitProgress; }
    public int engagementDays() { return engagementDays; }
    public long finishedDay() { return finishedDay; }
    public long revision() { return revision; }
    public String materializationId() { return "frontier:assault:" + id; }
    public boolean terminal() { return state == State.COMPLETED || state == State.ABORTED; }

    boolean depart() {
        if (state != State.ASSEMBLING) return false;
        state = State.EN_ROUTE;
        revision++;
        return true;
    }

    boolean advanceOutbound(FrontierPoint origin, FrontierPoint target) {
        if (state != State.EN_ROUTE) return false;
        transitProgress = Math.min(transitDays, transitProgress + 1);
        position = interpolate(origin, target, transitProgress, transitDays);
        if (transitProgress == transitDays) state = State.ENGAGING;
        revision++;
        return true;
    }

    boolean engageDay() {
        if (state != State.ENGAGING) return false;
        engagementDays++;
        revision++;
        return true;
    }

    boolean beginReturn() {
        if (state != State.ENGAGING) return false;
        state = State.RETURNING;
        transitProgress = 0;
        revision++;
        return true;
    }

    boolean advanceReturn(FrontierPoint target, FrontierPoint origin, long day) {
        if (state != State.RETURNING) return false;
        transitProgress = Math.min(transitDays, transitProgress + 1);
        position = interpolate(target, origin, transitProgress, transitDays);
        if (transitProgress == transitDays) {
            state = State.COMPLETED;
            finishedDay = day;
        }
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

    void restore(State state, FrontierPoint position, int transitProgress, int engagementDays,
                 long finishedDay, long revision) {
        this.state = Objects.requireNonNull(state, "state");
        this.position = Objects.requireNonNull(position, "position");
        if (transitProgress < 0 || transitProgress > transitDays || engagementDays < 0 || revision < 0
                || (terminal() && finishedDay < startedDay) || (!terminal() && finishedDay != -1)) {
            throw new IllegalArgumentException("invalid persisted assault state");
        }
        this.transitProgress = transitProgress;
        this.engagementDays = engagementDays;
        this.finishedDay = finishedDay;
        this.revision = revision;
    }

    static String idFor(String hiveId, long startedDay) {
        return "frontier:assault:" + hiveId.substring(hiveId.lastIndexOf(':') + 1) + ":day:"
                + String.format(Locale.ROOT, "%08d", startedDay);
    }

    static int travelDays(FrontierPoint origin, FrontierPoint target) {
        return FrontierBalance.hiveTravelDays(origin, target);
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
