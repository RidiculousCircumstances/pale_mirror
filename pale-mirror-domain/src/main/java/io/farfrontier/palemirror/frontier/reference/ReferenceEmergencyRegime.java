package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Active crisis authority derived from one settlement's current civic regime. */
public final class ReferenceEmergencyRegime {
    private final int settlementId;
    private final int activatedDay;
    private ReferenceCivicState state;
    private double warBudget;
    private int quarantineUntil;
    private String status = "active";

    ReferenceEmergencyRegime(int settlementId, ReferenceCivicState state, int activatedDay, double warBudget, int quarantineUntil) {
        this.settlementId = settlementId;
        this.state = Objects.requireNonNull(state, "state");
        this.activatedDay = activatedDay;
        this.warBudget = warBudget;
        this.quarantineUntil = quarantineUntil;
    }

    public int settlementId() { return settlementId; }
    public ReferenceCivicState state() { return state; }
    public int activatedDay() { return activatedDay; }
    public double warBudget() { return warBudget; }
    public int quarantineUntil() { return quarantineUntil; }
    public String status() { return status; }

    void state(ReferenceCivicState value) { state = Objects.requireNonNull(value, "state"); }
    void warBudget(double value) { warBudget = value; }
    void quarantineUntil(int value) { quarantineUntil = value; }
    void status(String value) { status = Objects.requireNonNull(value, "status"); }
}
