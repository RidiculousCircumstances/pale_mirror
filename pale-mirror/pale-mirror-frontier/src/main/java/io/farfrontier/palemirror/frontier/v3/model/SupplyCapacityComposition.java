package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPlanner;
import io.farfrontier.palemirror.frontier.v3.model.supply.ProductionCost;
import java.util.*;

/** Closed owner composition; missing/duplicate producers never inherit availability. */
public final class SupplyCapacityComposition {
    /** Explicit owner-supplied counterfactual, only for evaluating an uncommitted development option. */
    public record Option(SupplyCapacityPort.Key owner, List<SupplyPlanner.Process> processes) {
        public Option { Objects.requireNonNull(owner); processes = List.copyOf(processes); }
    }
    private static final Map<SupplyCapacityPort.Key, SupplyCapacityPort> PORTS = register(List.of(
            new BakerySupplyCapacity(), new FieldSupplyCapacity(), new ExtractionSupplyCapacity(), new AutomaticStationSupplyCapacity(),
            new IndividualStationSupplyCapacity(), new AttendedStockSupplyCapacity(), new ForestrySupplyCapacity()));
    private static final ImmutableKeyedInputView<GoodsParticipantScope, Map<String, SupplyPlanner.Stock>> STOCKS = new ImmutableKeyedInputView<>(GoodsParticipantState.MAX_PARTICIPANTS);
    private static final ImmutableKeyedInputView<GoodsParticipantScope, List<ProductionCost>> VALUATIONS = new ImmutableKeyedInputView<>(GoodsParticipantState.MAX_PARTICIPANTS);
    private SupplyCapacityComposition() { }
    public static Map<SupplyCapacityPort.Key, SupplyCapacityPort> register(List<SupplyCapacityPort> ports) {
        var result = new EnumMap<SupplyCapacityPort.Key, SupplyCapacityPort>(SupplyCapacityPort.Key.class);
        for (var port : ports) if (result.put(port.key(), port) != null) throw new IllegalArgumentException("duplicate supply capacity owner");
        if (result.size() != SupplyCapacityPort.Key.values().length) throw new IllegalArgumentException("missing supply capacity owner");
        return Map.copyOf(result);
    }
    /** Local replacement costs need physical capabilities, not a reserve or market plan.
     * No target/incoming promise is manufactured by this valuation-only projection. */
    static List<ProductionCost> forValuation(FrontierWorldState state, GoodsParticipant participant) {
        if (!state.bootstrap().ruleset().goodsTrade().supplyPlanning().enabled()) return List.of();
        var scope = GoodsParticipantScope.of(participant);
        var amounts = GoodsParticipantStorage.unclaimed(state, participant);
        var stocks = STOCKS.getPublished(state.bootstrap(), scope, List.of(amounts), () -> stocks(state, participant, amounts));
        var declarations = new ArrayList<List<ProductionCost>>(PORTS.size());
        for (var key : SupplyCapacityPort.Key.values()) declarations.add(PORTS.get(key).valuation(state, participant.settlementId(), stocks));
        return VALUATIONS.getPublished(state.bootstrap(), scope, new ArrayList<>(declarations), () -> {
            var result = new ArrayList<ProductionCost>();
            declarations.forEach(result::addAll);
            return List.copyOf(result);
        });
    }
    private static Map<String, SupplyPlanner.Stock> stocks(FrontierWorldState state, GoodsParticipant participant, Map<String, Integer> amounts) {
        var stocks = new TreeMap<String, SupplyPlanner.Stock>();
        var rules = state.bootstrap().ruleset().goodsTrade();
        for (var commodity : rules.policies().get(participant.policy())) {
            var kind = commodity.itemKind();
            stocks.put(kind, new SupplyPlanner.Stock(amounts.getOrDefault(kind, 0),
                    0, 0, 0, 0, 0, 1, rules.supplyPlanning().require(kind)));
        }
        return Map.copyOf(stocks);
    }
    public static List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks,
                                                  SupplyPlanningWindow window) {
        return read(state, home, stocks, window, Optional.empty());
    }
    static List<SupplyPlanner.Process> read(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks,
            SupplyPlanningWindow window, Optional<Option> option) {
        var result = new ArrayList<SupplyPlanner.Process>();
        for (var key : SupplyCapacityPort.Key.values()) result.addAll(option.filter(value -> value.owner() == key)
                .map(Option::processes).orElseGet(() -> PORTS.get(key).read(state, home, stocks, window)));
        return List.copyOf(result);
    }
    public static List<SupplyPlanner.Dependency> dependencies(FrontierWorldState state, SubjectId home, Map<String, SupplyPlanner.Stock> stocks) {
        var result = new ArrayList<SupplyPlanner.Dependency>();
        for (var key : SupplyCapacityPort.Key.values()) result.addAll(PORTS.get(key).dependencies(state, home, stocks));
        return List.copyOf(result);
    }
}
