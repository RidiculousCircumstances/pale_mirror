package io.farfrontier.palemirror.frontier.reference;

/** Mutable finite living matter of one source simulation cell. */
public final class ReferenceEcosystemCell {
    private double flora;
    private double fauna;
    private double detritus;
    private double nutrients;
    private final double moisture;
    private double scar;

    public ReferenceEcosystemCell(double flora, double fauna, double detritus, double nutrients, double moisture) {
        this.flora = flora;
        this.fauna = fauna;
        this.detritus = detritus;
        this.nutrients = nutrients;
        this.moisture = moisture;
    }

    public double flora() { return flora; }
    public void flora(double value) { flora = value; }
    public double fauna() { return fauna; }
    public void fauna(double value) { fauna = value; }
    public double detritus() { return detritus; }
    public void detritus(double value) { detritus = value; }
    public double nutrients() { return nutrients; }
    public void nutrients(double value) { nutrients = value; }
    public double moisture() { return moisture; }
    public double scar() { return scar; }
    public void scar(double value) { scar = value; }
    public double organicMass() { return flora + fauna + detritus; }
}
