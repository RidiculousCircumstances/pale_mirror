package io.farfrontier.palemirror.frontier.v3.model.market;

/** One cash-funded cargo lot. Fixed transport expense is not a per-item price. */
public record FundedCargoLot(int quantity, long goodsUnitPrice, long tripExpense) {
    public FundedCargoLot {
        if (quantity < 1 || goodsUnitPrice < 1 || tripExpense < 0)
            throw new IllegalArgumentException("invalid funded cargo lot");
    }
    public static int quantity(int wanted, long goodsUnitPrice, long tripExpense, long budget) {
        if (wanted < 0 || goodsUnitPrice < 1 || tripExpense < 0 || budget < 0)
            throw new IllegalArgumentException("invalid cargo funding");
        return (int) Math.min(wanted, Math.max(0, budget - tripExpense) / goodsUnitPrice);
    }
    public long deliveryPerItem() { return Math.ceilDiv(tripExpense, quantity); }
    public long totalCash() { return Math.addExact(Math.multiplyExact(quantity, goodsUnitPrice), tripExpense); }
}
