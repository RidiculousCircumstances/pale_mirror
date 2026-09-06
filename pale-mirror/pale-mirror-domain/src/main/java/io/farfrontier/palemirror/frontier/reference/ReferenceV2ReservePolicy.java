package io.farfrontier.palemirror.frontier.reference;

/** Source V2 public reserve policy for one settlement. */
public final class ReferenceV2ReservePolicy {
    private final int settlementId;
    private final double foodDays;
    private final double medicineDays;
    private final double ammoTarget;
    private boolean active = true;

    ReferenceV2ReservePolicy(int settlementId, double foodDays, double medicineDays, double ammoTarget) {
        this.settlementId = settlementId;
        this.foodDays = foodDays;
        this.medicineDays = medicineDays;
        this.ammoTarget = ammoTarget;
    }

    public int settlementId() { return settlementId; }
    public double foodDays() { return foodDays; }
    public double medicineDays() { return medicineDays; }
    public double ammoTarget() { return ammoTarget; }
    public boolean active() { return active; }

    void active(boolean value) { active = value; }
}
