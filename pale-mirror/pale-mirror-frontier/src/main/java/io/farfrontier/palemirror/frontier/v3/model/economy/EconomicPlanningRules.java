package io.farfrontier.palemirror.frontier.v3.model.economy;

/** Hashed settlement policy, not an executor cadence or an inventory reservation. */
public record EconomicPlanningRules(int horizonDays, int checkpoints, int maximumExportItems) {
    public EconomicPlanningRules {
        if (horizonDays < 2 || horizonDays > 16 || checkpoints < 2 || checkpoints > 16
                || maximumExportItems < 1 || maximumExportItems > 4096)
            throw new IllegalArgumentException("invalid bounded economic planning policy");
    }
    public String canonicalText() { return horizonDays + ":" + checkpoints + ":" + maximumExportItems; }
}
