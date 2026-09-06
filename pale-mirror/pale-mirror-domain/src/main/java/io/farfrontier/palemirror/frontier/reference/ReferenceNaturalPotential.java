package io.farfrontier.palemirror.frontier.reference;

/** Mutable natural-potential fields from Python {@code NaturalPotential}. */
public final class ReferenceNaturalPotential {
    private double fertility;
    private double oreRichness;
    private double forest;
    private double riverPower;

    public ReferenceNaturalPotential() {
        this(1.0d, 1.0d, 1.0d, 1.0d);
    }

    public ReferenceNaturalPotential(double fertility, double oreRichness, double forest, double riverPower) {
        this.fertility = fertility;
        this.oreRichness = oreRichness;
        this.forest = forest;
        this.riverPower = riverPower;
    }

    public double fertility() { return fertility; }
    public void fertility(double value) { fertility = value; }
    public double oreRichness() { return oreRichness; }
    public void oreRichness(double value) { oreRichness = value; }
    public double forest() { return forest; }
    public void forest(double value) { forest = value; }
    public double riverPower() { return riverPower; }
    public void riverPower(double value) { riverPower = value; }
}
