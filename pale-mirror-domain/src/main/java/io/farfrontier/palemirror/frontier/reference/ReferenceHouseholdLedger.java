package io.farfrontier.palemirror.frontier.reference;

/** Mutable household balance from Python {@code HouseholdLedger}. */
public final class ReferenceHouseholdLedger {
    private final double workers;
    private final double owners;
    private final double dependents;
    private double cash;
    private double wageIncome;
    private double dividendIncome;
    private double foodCoverage = 1.0d;

    public ReferenceHouseholdLedger(double workers, double owners, double dependents, double cash) {
        this.workers = workers;
        this.owners = owners;
        this.dependents = dependents;
        this.cash = cash;
    }

    public double workers() { return workers; }
    public double owners() { return owners; }
    public double dependents() { return dependents; }
    public double population() { return workers + owners + dependents; }
    public double cash() { return cash; }
    public void cash(double value) { cash = value; }
    public double wageIncome() { return wageIncome; }
    public void wageIncome(double value) { wageIncome = value; }
    public double dividendIncome() { return dividendIncome; }
    public void dividendIncome(double value) { dividendIncome = value; }
    public double foodCoverage() { return foodCoverage; }
    public void foodCoverage(double value) { foodCoverage = value; }
}
