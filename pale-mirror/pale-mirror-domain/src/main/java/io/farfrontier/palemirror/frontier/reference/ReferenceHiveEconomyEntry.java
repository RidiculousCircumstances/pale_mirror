package io.farfrontier.palemirror.frontier.reference;

/** Mutable per-day organ ledger mirroring the source model's diagnostic row. */
public final class ReferenceHiveEconomyEntry {
    private final int day;
    private final int organId;
    private final double openingBiomass;
    private double substrateIn;
    private double biomassIncome;
    private double samplesIn;
    private double maintenance;

    ReferenceHiveEconomyEntry(int day, ReferenceHiveOrgan organ) {
        this(day, organ.id(), organ.biomass());
    }

    ReferenceHiveEconomyEntry(int day, int organId, double openingBiomass) {
        this.day = day;
        this.organId = organId;
        this.openingBiomass = openingBiomass;
    }

    public int day() { return day; }
    public int organId() { return organId; }
    public double openingBiomass() { return openingBiomass; }
    public double substrateIn() { return substrateIn; }
    public double biomassIncome() { return biomassIncome; }
    public double samplesIn() { return samplesIn; }
    public double maintenance() { return maintenance; }
    void addSubstrateIn(double value) { substrateIn += value; }
    void addBiomassIncome(double value) { biomassIncome += value; }
    void addSamplesIn(double value) { samplesIn += value; }
    void maintenance(double value) { maintenance = value; }
}
