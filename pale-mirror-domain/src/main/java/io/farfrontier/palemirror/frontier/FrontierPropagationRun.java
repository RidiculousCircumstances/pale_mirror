package io.farfrontier.palemirror.frontier;

import java.util.Locale;
import java.util.Objects;

/** One non-combat carrier travelling from a live Sporulator to a distant foothold. */
public final class FrontierPropagationRun {
    public enum State { OUTBOUND, DEPLOYED, ABORTED }

    private final String id;
    private final String hiveId;
    private final String sourceOrganId;
    private final String bioformId;
    private final FrontierPoint origin;
    private final FrontierPoint target;
    private final long startedDay;
    private final int transitDays;
    private FrontierPoint position;
    private State state = State.OUTBOUND;
    private int transitProgress;
    private String latentColonyId;
    private long finishedDay = -1;
    private long revision;

    FrontierPropagationRun(String id, String hiveId, String sourceOrganId, String bioformId, FrontierPoint origin,
                           FrontierPoint target, long startedDay, int transitDays) {
        this.id = required(id, "id"); this.hiveId = required(hiveId, "hiveId");
        this.sourceOrganId = required(sourceOrganId, "sourceOrganId"); this.bioformId = required(bioformId, "bioformId");
        this.origin = Objects.requireNonNull(origin, "origin"); this.target = Objects.requireNonNull(target, "target");
        if (startedDay < 0 || transitDays < 1) throw new IllegalArgumentException("invalid propagation schedule");
        this.startedDay = startedDay; this.transitDays = transitDays; this.position = origin;
    }
    public String id() { return id; }
    public String hiveId() { return hiveId; }
    public String sourceOrganId() { return sourceOrganId; }
    public String bioformId() { return bioformId; }
    public FrontierPoint origin() { return origin; }
    public FrontierPoint target() { return target; }
    public long startedDay() { return startedDay; }
    public int transitDays() { return transitDays; }
    public FrontierPoint position() { return position; }
    public State state() { return state; }
    public int transitProgress() { return transitProgress; }
    public String latentColonyId() { return latentColonyId; }
    public long finishedDay() { return finishedDay; }
    public long revision() { return revision; }
    public String materializationId() { return "frontier:propagation-run:" + id; }
    public boolean terminal() { return state != State.OUTBOUND; }

    boolean advance(long day, String latentColonyId) {
        if (state != State.OUTBOUND) return false;
        transitProgress = Math.min(transitDays, transitProgress + 1);
        position = interpolate(origin, target, transitProgress, transitDays);
        if (transitProgress == transitDays) {
            state = State.DEPLOYED;
            this.latentColonyId = required(latentColonyId, "latentColonyId");
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
    void restore(State state, FrontierPoint position, int transitProgress, String latentColonyId,
                 long finishedDay, long revision) {
        this.state = Objects.requireNonNull(state, "state"); this.position = Objects.requireNonNull(position, "position");
        if (transitProgress < 0 || transitProgress > transitDays || revision < 0
                || (state == State.OUTBOUND && (latentColonyId != null || transitProgress >= transitDays || finishedDay != -1))
                || (state == State.DEPLOYED && (latentColonyId == null || latentColonyId.isBlank() || finishedDay < startedDay))
                || (state == State.ABORTED && finishedDay < startedDay)) throw new IllegalArgumentException("invalid persisted propagation");
        this.transitProgress = transitProgress; this.latentColonyId = latentColonyId; this.finishedDay = finishedDay; this.revision = revision;
    }
    static String idFor(String hiveId, String bioformId, long startedDay) {
        if (hiveId == null || hiveId.isBlank() || bioformId == null || bioformId.isBlank() || startedDay < 0) {
            throw new IllegalArgumentException("invalid propagation identity");
        }
        return "frontier:propagation-run:" + hiveId.substring(hiveId.lastIndexOf(':') + 1) + ":"
                + bioformId.substring(bioformId.lastIndexOf(':') + 1) + ":day:" + String.format(Locale.ROOT, "%08d", startedDay);
    }
    static int travelDays(FrontierPoint origin, FrontierPoint target) { return FrontierBalance.hiveTravelDays(origin, target); }
    private static FrontierPoint interpolate(FrontierPoint origin, FrontierPoint target, int progress, int total) {
        return new FrontierPoint(origin.x() + Math.round((target.x() - origin.x()) * progress / (float) total),
                origin.z() + Math.round((target.z() - origin.z()) * progress / (float) total));
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
