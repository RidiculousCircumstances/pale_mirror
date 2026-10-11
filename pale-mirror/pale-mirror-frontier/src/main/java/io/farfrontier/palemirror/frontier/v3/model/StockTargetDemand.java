package io.farfrontier.palemirror.frontier.v3.model;

/** Commodity-neutral policy calculation. Capacity and custody are separate admission constraints. */
public record StockTargetDemand(long target, long held, long incoming, long outgoing) {
    public StockTargetDemand {
        if (target < 0 || held < 0 || incoming < 0 || outgoing < 0)
            throw new IllegalArgumentException("negative stock target or supply");
    }
    public long covered() { return Math.max(0, Math.subtractExact(Math.addExact(held, incoming), outgoing)); }
    public long missing() { return Math.max(0, Math.subtractExact(target, covered())); }
    public boolean requested() { return missing() > 0; }
    public static int target(GoodsTradeRules.Commodity commodity, int residents, int protectedMinimum) {
        if (protectedMinimum < 0) throw new IllegalArgumentException("negative protected stock");
        return Math.max(commodity.target(residents), protectedMinimum);
    }
}
