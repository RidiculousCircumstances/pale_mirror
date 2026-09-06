package io.farfrontier.palemirror.frontier;

import java.util.Locale;
import java.util.Objects;

/**
 * One bounded biological forage trip.  It owns the cargo until it is physically returned to a
 * connected Core or Digestive Pool; leaving a forage cell is not a hidden biomass transfer.
 */
public final class FrontierHarvesterRun {
    public enum State { OUTBOUND, FORAGING, RETURNING, COMPLETED, ABORTED }

    private final String id;
    private final String hiveId;
    private final String sourceOrganId;
    private final String bioformId;
    private final FrontierPoint origin;
    private final FrontierPoint foragePosition;
    private final long startedDay;
    private final int outboundDays;
    private FrontierPoint position;
    private String receiverOrganId;
    private State state = State.OUTBOUND;
    private int transitProgress;
    private int returnDays;
    private long cargo;
    private long geneticCargo;
    private long finishedDay = -1;
    private long revision;

    FrontierHarvesterRun(String id, String hiveId, String sourceOrganId, String bioformId, FrontierPoint origin,
                         FrontierPoint foragePosition, long startedDay, int outboundDays) {
        this.id = required(id, "id");
        this.hiveId = required(hiveId, "hiveId");
        this.sourceOrganId = required(sourceOrganId, "sourceOrganId");
        this.bioformId = required(bioformId, "bioformId");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.foragePosition = Objects.requireNonNull(foragePosition, "foragePosition");
        if (startedDay < 0 || outboundDays < 1) throw new IllegalArgumentException("invalid harvester schedule");
        this.startedDay = startedDay;
        this.outboundDays = outboundDays;
        this.position = origin;
    }

    public String id() { return id; }
    public String hiveId() { return hiveId; }
    public String sourceOrganId() { return sourceOrganId; }
    public String bioformId() { return bioformId; }
    public FrontierPoint origin() { return origin; }
    public FrontierPoint foragePosition() { return foragePosition; }
    public long startedDay() { return startedDay; }
    public int outboundDays() { return outboundDays; }
    public FrontierPoint position() { return position; }
    public String receiverOrganId() { return receiverOrganId; }
    public State state() { return state; }
    public int transitProgress() { return transitProgress; }
    public int returnDays() { return returnDays; }
    public long cargo() { return cargo; }
    public long geneticCargo() { return geneticCargo; }
    public long finishedDay() { return finishedDay; }
    public long revision() { return revision; }
    public String materializationId() { return "frontier:harvester:" + id; }
    public boolean terminal() { return state == State.COMPLETED || state == State.ABORTED; }

    boolean advanceOutbound() {
        if (state != State.OUTBOUND) return false;
        transitProgress = Math.min(outboundDays, transitProgress + 1);
        position = interpolate(origin, foragePosition, transitProgress, outboundDays);
        if (transitProgress == outboundDays) state = State.FORAGING;
        revision++;
        return true;
    }

    boolean beginReturn(String receiverOrganId, FrontierPoint receiverPosition, FrontierEcology.Harvest harvest) {
        if (state != State.FORAGING) return false;
        this.receiverOrganId = required(receiverOrganId, "receiverOrganId");
        this.cargo = harvest.biomass();
        this.geneticCargo = harvest.geneticMaterial();
        this.returnDays = travelDays(foragePosition, Objects.requireNonNull(receiverPosition, "receiverPosition"));
        this.transitProgress = 0;
        this.state = State.RETURNING;
        this.position = foragePosition;
        revision++;
        return true;
    }

    boolean advanceReturn(FrontierPoint receiverPosition, long day) {
        if (state != State.RETURNING) return false;
        transitProgress = Math.min(returnDays, transitProgress + 1);
        position = interpolate(foragePosition, receiverPosition, transitProgress, returnDays);
        if (transitProgress == returnDays) {
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

    void restore(State state, FrontierPoint position, String receiverOrganId, int transitProgress, int returnDays,
                 long cargo, long geneticCargo, long finishedDay, long revision) {
        this.state = Objects.requireNonNull(state, "state");
        this.position = Objects.requireNonNull(position, "position");
        if (transitProgress < 0 || returnDays < 0 || cargo < 0 || geneticCargo < 0 || revision < 0
                || (state == State.OUTBOUND && (transitProgress > outboundDays || returnDays != 0 || receiverOrganId != null
                || cargo != 0 || geneticCargo != 0))
                || (state == State.FORAGING && (transitProgress != outboundDays || returnDays != 0 || receiverOrganId != null
                || cargo != 0 || geneticCargo != 0))
                || (state == State.RETURNING && (receiverOrganId == null || receiverOrganId.isBlank() || returnDays < 1
                || transitProgress > returnDays || cargo < 1))
                || (terminal() && finishedDay < startedDay) || (!terminal() && finishedDay != -1)) {
            throw new IllegalArgumentException("invalid persisted harvester run state");
        }
        this.receiverOrganId = receiverOrganId;
        this.transitProgress = transitProgress;
        this.returnDays = returnDays;
        this.cargo = cargo;
        this.geneticCargo = geneticCargo;
        this.finishedDay = finishedDay;
        this.revision = revision;
    }

    static String idFor(String hiveId, String bioformId, long startedDay) {
        if (hiveId == null || hiveId.isBlank() || bioformId == null || bioformId.isBlank() || startedDay < 0) {
            throw new IllegalArgumentException("invalid harvester identity");
        }
        return "frontier:harvester:" + hiveId.substring(hiveId.lastIndexOf(':') + 1) + ":"
                + bioformId.substring(bioformId.lastIndexOf(':') + 1) + ":day:"
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
