package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Closed composition names owners; the stock policy does not inspect their job types or stages. */
public final class StockSupplyComposition {
    private static final Map<StockSupplyPort.Key, StockSupplyPort> PORTS = register(List.of(
            new BakeryStockSupply(), new HarvestStockSupply(), new ExtractionStockSupply(), new TradeStockSupply(), new AutomaticStationStockSupply()));
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
    static List<StockSupplyPort.Commitment> pending(FrontierWorldState state) {
        return VIEW.get(List.of(state.productionJobs(), state.productionStations(), state.resourceSites(), state.extractionSites(),
                state.inventory(), state.companies().goodsTrade().contracts(), state.companies().goodsTrade().participants(),
                state.humanPopulation(), LivingResidentStockView.counts(state), state.shipments(), state.bootstrap()), () -> {
            var result = new ArrayList<StockSupplyPort.Commitment>();
            for (var key : StockSupplyPort.Key.values()) result.addAll(PORTS.get(key).pending(state));
            return List.copyOf(result);
        });
    }
}
