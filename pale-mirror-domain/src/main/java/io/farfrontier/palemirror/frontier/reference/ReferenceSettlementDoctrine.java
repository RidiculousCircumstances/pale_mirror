package io.farfrontier.palemirror.frontier.reference;

/** Deterministic local political disposition from Python {@code SettlementDoctrine}. */
public final class ReferenceSettlementDoctrine {
    private final double caution;
    private double solidarity;
    private final double commercialDependence;
    private final double militancy;
    private final double casualtyTolerance;
    private final double quarantineWillingness;
    private double legitimacy = 0.70d;

    ReferenceSettlementDoctrine(double caution, double solidarity, double commercialDependence, double militancy,
                                double casualtyTolerance, double quarantineWillingness) {
        this.caution = caution;
        this.solidarity = solidarity;
        this.commercialDependence = commercialDependence;
        this.militancy = militancy;
        this.casualtyTolerance = casualtyTolerance;
        this.quarantineWillingness = quarantineWillingness;
    }

    public double caution() { return caution; }
    public double solidarity() { return solidarity; }
    public double commercialDependence() { return commercialDependence; }
    public double militancy() { return militancy; }
    public double casualtyTolerance() { return casualtyTolerance; }
    public double quarantineWillingness() { return quarantineWillingness; }
    public double legitimacy() { return legitimacy; }

    void solidarity(double value) { solidarity = value; }
    void legitimacy(double value) { legitimacy = value; }
}
