package io.farfrontier.palemirror.domain;

import java.util.Objects;

public final class FacilityState {
    private final WorldObjectId id;
    private final InfectionSourceId infectionSource;
    private final int normalProduction;
    private final int infectionThreshold;
    private int infectionPressure;
    private int currentProduction;
    private int recoveryStepsRemaining;
    private long desiredRevision;
    private long observedRevision;
    private FacilityStatus status;
    private ThreatTier threatTier;
    private long threatStartedAtStep;
    private final SiegeState siege;

    public FacilityState(WorldObjectId id, int normalProduction, int infectionThreshold, int infectionPressure) {
        this(id, InfectionSourceId.CRIMSON, normalProduction, infectionThreshold, infectionPressure);
    }

    public FacilityState(WorldObjectId id, InfectionSourceId infectionSource, int normalProduction,
                         int infectionThreshold, int infectionPressure) {
        this(id, infectionSource, normalProduction, infectionThreshold, infectionPressure, normalProduction, 0, 0, 0,
                FacilityStatus.OPERATIONAL, ThreatTier.DORMANT, 0, new SiegeState());
    }

    public FacilityState(WorldObjectId id, int normalProduction, int infectionThreshold, int infectionPressure,
                         int currentProduction, int recoveryStepsRemaining, long desiredRevision,
                         long observedRevision, FacilityStatus status) {
        this(id, InfectionSourceId.CRIMSON, normalProduction, infectionThreshold, infectionPressure, currentProduction, recoveryStepsRemaining,
                desiredRevision, observedRevision, status,
                status == FacilityStatus.INFECTED ? ThreatTier.FOOTHOLD : ThreatTier.DORMANT, 0, new SiegeState());
    }

    public FacilityState(WorldObjectId id, int normalProduction, int infectionThreshold, int infectionPressure,
                         int currentProduction, int recoveryStepsRemaining, long desiredRevision,
                         long observedRevision, FacilityStatus status, ThreatTier threatTier, long threatStartedAtStep) {
        this(id, InfectionSourceId.CRIMSON, normalProduction, infectionThreshold, infectionPressure, currentProduction, recoveryStepsRemaining,
                desiredRevision, observedRevision, status, threatTier, threatStartedAtStep, new SiegeState());
    }

    public FacilityState(WorldObjectId id, int normalProduction, int infectionThreshold, int infectionPressure,
                         int currentProduction, int recoveryStepsRemaining, long desiredRevision,
                         long observedRevision, FacilityStatus status, ThreatTier threatTier, long threatStartedAtStep,
                         SiegeState siege) {
        this(id, InfectionSourceId.CRIMSON, normalProduction, infectionThreshold, infectionPressure, currentProduction,
                recoveryStepsRemaining, desiredRevision, observedRevision, status, threatTier, threatStartedAtStep, siege);
    }

    public FacilityState(WorldObjectId id, InfectionSourceId infectionSource, int normalProduction, int infectionThreshold,
                         int infectionPressure, int currentProduction, int recoveryStepsRemaining, long desiredRevision,
                         long observedRevision, FacilityStatus status, ThreatTier threatTier, long threatStartedAtStep,
                         SiegeState siege) {
        this.id = Objects.requireNonNull(id, "id");
        this.infectionSource = Objects.requireNonNull(infectionSource, "infectionSource");
        this.normalProduction = normalProduction;
        this.infectionThreshold = infectionThreshold;
        this.infectionPressure = infectionPressure;
        this.currentProduction = currentProduction;
        this.recoveryStepsRemaining = recoveryStepsRemaining;
        this.desiredRevision = desiredRevision;
        this.observedRevision = observedRevision;
        this.status = Objects.requireNonNull(status, "status");
        this.threatTier = Objects.requireNonNull(threatTier, "threatTier");
        this.threatStartedAtStep = threatStartedAtStep;
        this.siege = Objects.requireNonNull(siege, "siege");
    }

    public WorldObjectId id() { return id; }
    public InfectionSourceId infectionSource() { return infectionSource; }
    public int normalProduction() { return normalProduction; }
    public int infectionThreshold() { return infectionThreshold; }
    public int infectionPressure() { return infectionPressure; }
    public int currentProduction() { return currentProduction; }
    public int recoveryStepsRemaining() { return recoveryStepsRemaining; }
    public long desiredRevision() { return desiredRevision; }
    public long observedRevision() { return observedRevision; }
    public FacilityStatus status() { return status; }
    public ThreatTier threatTier() { return threatTier; }
    public long threatStartedAtStep() { return threatStartedAtStep; }
    public SiegeState siege() { return siege; }
    public void setObservedRevision(long revision) { observedRevision = Math.max(observedRevision, revision); }
    public void infect() { infect(0); }
    public void infect(long simulationStep) {
        status = FacilityStatus.INFECTED;
        currentProduction = 0;
        threatTier = ThreatTier.FOOTHOLD;
        threatStartedAtStep = simulationStep;
        siege.reset();
        desiredRevision++;
    }
    public boolean advanceThreatTier(long simulationStep) { return advanceThreatTier(simulationStep, ThreatTierPolicy.DEFAULT); }
    public boolean advanceThreatTier(long simulationStep, ThreatTierPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (status != FacilityStatus.INFECTED) return false;
        long activeSteps = Math.max(0, simulationStep - threatStartedAtStep);
        ThreatTier next = policy.next(threatTier, activeSteps);
        if (next == threatTier) return false;
        threatTier = next;
        if (next == ThreatTier.APEX) siege.pending();
        desiredRevision++;
        return true;
    }
    public void beginRecovery(int steps) {
        status = FacilityStatus.RECOVERING;
        recoveryStepsRemaining = Math.max(1, steps);
        threatTier = ThreatTier.DORMANT;
        threatStartedAtStep = 0;
        siege.reset();
        desiredRevision++;
    }
    public boolean advanceRecovery() {
        if (status != FacilityStatus.RECOVERING) return false;
        recoveryStepsRemaining--;
        if (recoveryStepsRemaining > 0) return false;
        status = FacilityStatus.OPERATIONAL;
        currentProduction = normalProduction;
        infectionPressure = 0;
        threatTier = ThreatTier.DORMANT;
        threatStartedAtStep = 0;
        siege.reset();
        desiredRevision++;
        return true;
    }

    public boolean activateSiege(String definitionId, String definitionVersion, String bossProfileId) {
        if (!siege.activate(definitionId, definitionVersion, bossProfileId)) return false;
        desiredRevision++;
        return true;
    }

    public boolean bypassSiege() {
        if (!siege.bypass()) return false;
        desiredRevision++;
        return true;
    }

    public boolean siegeGateDestroyed(String slotId) {
        if (!siege.gateDestroyed(slotId)) return false;
        desiredRevision++;
        return true;
    }

    public boolean controllerVulnerable() { return siege.controllerVulnerable(); }
}
