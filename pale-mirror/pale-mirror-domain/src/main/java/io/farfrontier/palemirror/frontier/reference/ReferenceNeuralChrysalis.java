package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** Bounded, cancellable reconstitution commitment owned by the V2 hive state. */
public final class ReferenceNeuralChrysalis {
    private final int organId;
    private final String sectorKey;
    private final int startedDay;
    private final double biomassCommitted;
    private int daysRemaining;
    private String status = "forming";

    ReferenceNeuralChrysalis(int organId, String sectorKey, int startedDay, int daysRemaining, double biomassCommitted) {
        this.organId = organId;
        this.sectorKey = Objects.requireNonNull(sectorKey, "sectorKey");
        this.startedDay = startedDay;
        this.daysRemaining = daysRemaining;
        this.biomassCommitted = biomassCommitted;
    }

    public int organId() { return organId; }
    public String sectorKey() { return sectorKey; }
    public int startedDay() { return startedDay; }
    public int daysRemaining() { return daysRemaining; }
    public double biomassCommitted() { return biomassCommitted; }
    public String status() { return status; }

    void daysRemaining(int value) { daysRemaining = value; }
    void status(String value) { status = Objects.requireNonNull(value, "status"); }
}
