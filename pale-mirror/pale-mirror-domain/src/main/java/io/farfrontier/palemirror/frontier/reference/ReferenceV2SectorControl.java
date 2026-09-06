package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Durable control claim for one operational sector, owned by {@link ReferenceV2State}. */
public final class ReferenceV2SectorControl {
    private final String sectorKey;
    private ReferenceSectorControlState state = ReferenceSectorControlState.CONTESTED;
    private double cordonStrength;
    private double garrison;
    private boolean supplied;
    private int lastClearedDay = -10_000;
    private int lastChangedDay;
    private int heldDays;
    private String reason = "unclaimed frontier";

    ReferenceV2SectorControl(String sectorKey) { this.sectorKey = Objects.requireNonNull(sectorKey, "sectorKey"); }

    public String sectorKey() { return sectorKey; }
    public ReferenceSectorControlState state() { return state; }
    public double cordonStrength() { return cordonStrength; }
    public double garrison() { return garrison; }
    public boolean supplied() { return supplied; }
    public int lastClearedDay() { return lastClearedDay; }
    public int lastChangedDay() { return lastChangedDay; }
    public int heldDays() { return heldDays; }
    public String reason() { return reason; }

    void state(ReferenceSectorControlState value) { state = Objects.requireNonNull(value, "state"); }
    void cordonStrength(double value) { cordonStrength = value; }
    void garrison(double value) { garrison = value; }
    void supplied(boolean value) { supplied = value; }
    void lastClearedDay(int value) { lastClearedDay = value; }
    void lastChangedDay(int value) { lastChangedDay = value; }
    void heldDays(int value) { heldDays = value; }
    void reason(String value) { reason = Objects.requireNonNull(value, "reason"); }
}
