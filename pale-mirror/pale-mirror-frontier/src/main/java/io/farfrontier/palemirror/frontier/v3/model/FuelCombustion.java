package io.farfrontier.palemirror.frontier.v3.model;

/** One ignited item. Its finite simulation-time interval expires even without work or an observer. */
public record FuelCombustion(String itemKind, long startedAtTick, long endsAtTick) {
    public FuelCombustion {
        ProductionRecipe.requireItemKey(itemKind);
        if (startedAtTick < 0 || endsAtTick <= startedAtTick || endsAtTick - startedAtTick > 1_000_000_000L)
            throw new IllegalArgumentException("invalid finite fuel-combustion interval");
    }
    public static FuelCombustion ignite(ProductionCatalog.Fuel fuel, long tick) {
        return new FuelCombustion(fuel.itemKind(), tick, Math.addExact(tick, fuel.burnTicks()));
    }
    public long remainingAt(long tick) {
        if (tick < startedAtTick) throw new IllegalArgumentException("combustion cannot be observed before ignition");
        return Math.max(0L, endsAtTick - tick);
    }
    public boolean burningAt(long tick) { return remainingAt(tick) > 0; }
}
