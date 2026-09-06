package io.farfrontier.palemirror.frontier.reference;

/** Mutable built-facility capacity from Python {@code Facilities}. */
public final class ReferenceFacilities {
    private double workshop;
    private double armory;
    private double clinic;
    private double fortification;

    public ReferenceFacilities() {
        this(0.5d, 0.25d, 0.25d, 0.5d);
    }

    public ReferenceFacilities(double workshop, double armory, double clinic, double fortification) {
        this.workshop = workshop;
        this.armory = armory;
        this.clinic = clinic;
        this.fortification = fortification;
    }

    public double workshop() { return workshop; }
    public void workshop(double value) { workshop = value; }
    public double armory() { return armory; }
    public void armory(double value) { armory = value; }
    public double clinic() { return clinic; }
    public void clinic(double value) { clinic = value; }
    public double fortification() { return fortification; }
    public void fortification(double value) { fortification = value; }

    public double productiveTotal() {
        return workshop + armory + clinic;
    }
}
