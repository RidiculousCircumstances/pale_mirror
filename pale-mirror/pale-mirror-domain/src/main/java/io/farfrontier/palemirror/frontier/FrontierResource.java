package io.farfrontier.palemirror.frontier;

public enum FrontierResource {
    FOOD(1), ORE(2), WOOD(2), MEDICINE(5), WEAPONS(8), AMMO(1), POWER(1);
    private final long referenceCredit;
    FrontierResource(long referenceCredit) { this.referenceCredit = referenceCredit; }
    public long referenceCredit() { return referenceCredit; }
}
