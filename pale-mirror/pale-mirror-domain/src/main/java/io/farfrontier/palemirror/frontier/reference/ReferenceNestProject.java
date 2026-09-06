package io.farfrontier.palemirror.frontier.reference;

/** A cancellable source-port morphogenesis commitment. */
public final class ReferenceNestProject {
    private final int sourceOrganId;
    private final int x;
    private final int y;
    private final ReferenceOrganKind kind;
    private final double committedBiomass;
    private int daysRemaining;

    ReferenceNestProject(int sourceOrganId, int x, int y, int daysRemaining, ReferenceOrganKind kind, double committedBiomass) {
        this.sourceOrganId = sourceOrganId; this.x = x; this.y = y; this.daysRemaining = daysRemaining;
        this.kind = kind; this.committedBiomass = committedBiomass;
    }

    public int sourceOrganId() { return sourceOrganId; }
    public int x() { return x; }
    public int y() { return y; }
    public int daysRemaining() { return daysRemaining; }
    void daysRemaining(int value) { daysRemaining = value; }
    public ReferenceOrganKind kind() { return kind; }
    public double committedBiomass() { return committedBiomass; }
}
