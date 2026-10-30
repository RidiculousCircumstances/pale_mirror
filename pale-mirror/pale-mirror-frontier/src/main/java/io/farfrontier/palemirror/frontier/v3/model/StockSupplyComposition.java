package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Closed composition names owners; the stock policy does not inspect their job types or stages. */
public final class StockSupplyComposition {
    private static final Map<StockSupplyPort.Key, StockSupplyPort> PORTS = register(List.of(
            new StationWorkStockSupply(), new HarvestStockSupply(), new ExtractionStockSupply(), new TradeStockSupply(), new AutomaticStationStockSupply(), new ForestryStockSupply()));
    private static final ImmutableInputView<List<StockSupplyPort.Commitment>> VIEW = new ImmutableInputView<>();
    private StockSupplyComposition() { }
    /** Read one producer's promises without re-entering commercial consent/reserve calculations. */
    public static List<StockSupplyPort.Commitment> pending(FrontierWorldState state, StockSupplyPort.Key owner) {
        return PORTS.get(Objects.requireNonNull(owner)).pending(state);
    }
    static long inputDemand(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId owner, String itemKind, long atTick) {
        long quantity = 0;
        for (var key : StockSupplyPort.Key.values()) quantity = Math.addExact(quantity, PORTS.get(key).inputDemand(state, owner, itemKind, atTick));
        return quantity;
    }
    public static Map<StockSupplyPort.Key, StockSupplyPort> register(List<StockSupplyPort> ports) {
        var result = new EnumMap<StockSupplyPort.Key, StockSupplyPort>(StockSupplyPort.Key.class);
        for (var port : ports) if (result.put(port.key(), port) != null)
            throw new IllegalArgumentException("duplicate stock supply owner");
        if (result.size() != StockSupplyPort.Key.values().length) throw new IllegalArgumentException("missing stock supply owner");
        return Map.copyOf(result);
    }
    private record StockKey(io.farfrontier.palemirror.frontier.v3.api.SubjectId owner, String item) { }
    private static final ImmutableInputView<Map<StockKey, List<StockSupplyPort.Commitment>>> BY_STOCK = new ImmutableInputView<>();
    /** Addressed read of the same promises; exclusions remain with the consumer. */
    public static List<StockSupplyPort.Commitment> pending(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId owner, String item) {
        var all = pending(state);
        var groups = BY_STOCK.get(List.of(all), () -> {
            var result = new HashMap<StockKey, List<StockSupplyPort.Commitment>>();
            for (var promise : all) result.computeIfAbsent(new StockKey(promise.economicOwner(), promise.itemKind()),
                    ignored -> new ArrayList<>()).add(promise);
            result.replaceAll((key, rows) -> List.copyOf(rows));
            return ImmutableLookupMap.copyOf(result);
        });
        return groups.getOrDefault(new StockKey(Objects.requireNonNull(owner), Objects.requireNonNull(item)), List.of());
    }
    static List<StockSupplyPort.Commitment> pending(FrontierWorldState state) {
        var rows = new ArrayList<List<StockSupplyPort.Commitment>>();
        var inputs = new ArrayList<Object>();
        for (var key : StockSupplyPort.Key.values()) {
            var current = PORTS.get(key).pending(state); rows.add(current); inputs.add(current);
        }
        // Each producer supplies its complete current facts. An unrelated world
        // root cannot invalidate them, and a newly added owner cannot be omitted
        // from a manually maintained aggregate dependency tuple (as forestry was).
        return VIEW.get(inputs, () -> rows.stream().flatMap(List::stream).toList());
    }
}
