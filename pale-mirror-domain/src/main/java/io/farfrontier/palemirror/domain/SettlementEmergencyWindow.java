package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Fairness window for a known community; it exists independently from scenario presentation. */
public final class SettlementEmergencyWindow {
    private final WorldObjectId communityId;
    private final long openedAtStep;
    private final long graceSteps;
    private long remainingGraceSteps;
    private final AudienceRegionReachability reachabilityAtOpen;
    private EmergencyWindowState state;

    public SettlementEmergencyWindow(WorldObjectId communityId, long openedAtStep, long deadlineStep,
                                     EmergencyWindowState state) {
        this(communityId, openedAtStep, deadlineStep - openedAtStep, deadlineStep - openedAtStep,
                AudienceRegionReachability.LOCAL, state);
    }

    public SettlementEmergencyWindow(WorldObjectId communityId, long openedAtStep, long graceSteps,
                                     long remainingGraceSteps, AudienceRegionReachability reachabilityAtOpen,
                                     EmergencyWindowState state) {
        this.communityId = Objects.requireNonNull(communityId, "communityId");
        if (openedAtStep < 0 || graceSteps < 1 || remainingGraceSteps < 0 || remainingGraceSteps > graceSteps) {
            throw new IllegalArgumentException("Invalid emergency window timing");
        }
        this.openedAtStep = openedAtStep;
        this.graceSteps = graceSteps;
        this.remainingGraceSteps = remainingGraceSteps;
        this.reachabilityAtOpen = Objects.requireNonNull(reachabilityAtOpen, "reachabilityAtOpen");
        this.state = Objects.requireNonNull(state, "state");
    }
    public WorldObjectId communityId() { return communityId; }
    public long openedAtStep() { return openedAtStep; }
    /** Historical projected deadline; presentation should prefer the pause-aware remaining value. */
    public long deadlineStep() { return openedAtStep + graceSteps; }
    public long graceSteps() { return graceSteps; }
    public long remainingGraceSteps() { return remainingGraceSteps; }
    public AudienceRegionReachability reachabilityAtOpen() { return reachabilityAtOpen; }
    public EmergencyWindowState state() { return state; }
    /** Offline audiences do not spend irreversible intervention time. */
    public boolean elapse(boolean audiencePresent) {
        if (state != EmergencyWindowState.OPEN || !audiencePresent || remainingGraceSteps == 0) return false;
        remainingGraceSteps--;
        return remainingGraceSteps == 0;
    }
    public boolean beginEvacuation() { if (state != EmergencyWindowState.OPEN) return false; state = EmergencyWindowState.EVACUATING; return true; }
    public boolean close() { if (state != EmergencyWindowState.OPEN) return false; state = EmergencyWindowState.CLOSED; return true; }
    public boolean resolve() { if (state != EmergencyWindowState.EVACUATING) return false; state = EmergencyWindowState.RESOLVED; return true; }
}
