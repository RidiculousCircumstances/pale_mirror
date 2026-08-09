package io.farfrontier.palemirror.domain;

import java.util.Objects;

public final class FacilityState {
    private final WorldObjectId id;
    private final int normalProduction;
    private final int infectionThreshold;
    private int infectionPressure;
    private int currentProduction;
    private int recoveryStepsRemaining;
    private long desiredRevision;
    private long observedRevision;
    private FacilityStatus status;

    public FacilityState(WorldObjectId id, int normalProduction, int infectionThreshold, int infectionPressure) {
        this(id, normalProduction, infectionThreshold, infectionPressure, normalProduction, 0, 0, 0, FacilityStatus.OPERATIONAL);
    }

    public FacilityState(WorldObjectId id, int normalProduction, int infectionThreshold, int infectionPressure,
                         int currentProduction, int recoveryStepsRemaining, long desiredRevision,
                         long observedRevision, FacilityStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.normalProduction = normalProduction;
        this.infectionThreshold = infectionThreshold;
        this.infectionPressure = infectionPressure;
        this.currentProduction = currentProduction;
        this.recoveryStepsRemaining = recoveryStepsRemaining;
        this.desiredRevision = desiredRevision;
        this.observedRevision = observedRevision;
        this.status = Objects.requireNonNull(status, "status");
    }

    public WorldObjectId id() { return id; }
    public int normalProduction() { return normalProduction; }
    public int infectionThreshold() { return infectionThreshold; }
    public int infectionPressure() { return infectionPressure; }
    public int currentProduction() { return currentProduction; }
    public int recoveryStepsRemaining() { return recoveryStepsRemaining; }
    public long desiredRevision() { return desiredRevision; }
    public long observedRevision() { return observedRevision; }
    public FacilityStatus status() { return status; }
    public void setObservedRevision(long revision) { observedRevision = Math.max(observedRevision, revision); }
    public void infect() { status = FacilityStatus.INFECTED; currentProduction = 0; desiredRevision++; }
    public void beginRecovery(int steps) { status = FacilityStatus.RECOVERING; recoveryStepsRemaining = Math.max(1, steps); desiredRevision++; }
    public boolean advanceRecovery() {
        if (status != FacilityStatus.RECOVERING) return false;
        recoveryStepsRemaining--;
        if (recoveryStepsRemaining > 0) return false;
        status = FacilityStatus.OPERATIONAL;
        currentProduction = normalProduction;
        infectionPressure = 0;
        desiredRevision++;
        return true;
    }
}
