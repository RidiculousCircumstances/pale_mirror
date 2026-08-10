package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Fairness window for a known community; it exists independently from scenario presentation. */
public final class SettlementEmergencyWindow {
    private final WorldObjectId communityId;
    private final long openedAtStep;
    private final long deadlineStep;
    private EmergencyWindowState state;

    public SettlementEmergencyWindow(WorldObjectId communityId, long openedAtStep, long deadlineStep,
                                     EmergencyWindowState state) {
        this.communityId = Objects.requireNonNull(communityId, "communityId");
        if (openedAtStep < 0 || deadlineStep < openedAtStep) throw new IllegalArgumentException("Invalid emergency window timing");
        this.openedAtStep = openedAtStep;
        this.deadlineStep = deadlineStep;
        this.state = Objects.requireNonNull(state, "state");
    }
    public WorldObjectId communityId() { return communityId; }
    public long openedAtStep() { return openedAtStep; }
    public long deadlineStep() { return deadlineStep; }
    public EmergencyWindowState state() { return state; }
    public boolean beginEvacuation() { if (state != EmergencyWindowState.OPEN) return false; state = EmergencyWindowState.EVACUATING; return true; }
    public boolean close() { if (state != EmergencyWindowState.OPEN) return false; state = EmergencyWindowState.CLOSED; return true; }
    public boolean resolve() { if (state != EmergencyWindowState.EVACUATING) return false; state = EmergencyWindowState.RESOLVED; return true; }
}
