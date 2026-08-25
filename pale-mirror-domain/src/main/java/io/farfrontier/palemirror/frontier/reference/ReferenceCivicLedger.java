package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Current civic regime, held per settlement by the V2 root. */
public final class ReferenceCivicLedger {
    private ReferenceCivicState state = ReferenceCivicState.NORMAL;
    private int enteredDay;
    private double foodReserveDays;
    private double legitimacy;
    private int quarantineUntil;
    private double warBudget;
    private double rationFraction = 1.0d;
    private String reason = "initial civic order";

    ReferenceCivicLedger(double legitimacy) { this.legitimacy = legitimacy; }

    public ReferenceCivicState state() { return state; }
    public int enteredDay() { return enteredDay; }
    public double foodReserveDays() { return foodReserveDays; }
    public double legitimacy() { return legitimacy; }
    public int quarantineUntil() { return quarantineUntil; }
    public double warBudget() { return warBudget; }
    public double rationFraction() { return rationFraction; }
    public String reason() { return reason; }

    void state(ReferenceCivicState value) { state = Objects.requireNonNull(value, "state"); }
    void enteredDay(int value) { enteredDay = value; }
    void foodReserveDays(double value) { foodReserveDays = value; }
    void legitimacy(double value) { legitimacy = value; }
    void quarantineUntil(int value) { quarantineUntil = value; }
    void warBudget(double value) { warBudget = value; }
    void rationFraction(double value) { rationFraction = value; }
    void reason(String value) { reason = Objects.requireNonNull(value, "reason"); }
}
