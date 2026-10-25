package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.ProductionRequirement;
import java.util.*;

/** One shared projection; no market-view recursion or family-specific dispatch in the coordinator. */
public final class SettlementReservePlanning {
    private static final Map<ReserveSupplyPort.Key, ReserveSupplyPort> PORTS = register(List.of(
            new NutritionReserveSupply(), new BakeryReserveSupply(), new FieldReserveSupply(), new ExtractionReserveSupply(), new LogisticsReserveSupply(), new AutomaticStationReserveSupply(), new EquipmentReserveSupply()));
    private static final ImmutableInputView<Map<SubjectId, Map<String, Target>>> CACHE = new ImmutableInputView<>();
    private static final ImmutableInputView<Map<SubjectId, Basis>> BASIS = new ImmutableInputView<>();
    private record InstantKey(SubjectId home, long atTick) { }
    private static final ImmutableInputView<Map<InstantKey, Basis>> DATED = new ImmutableInputView<>();
    public record Basis(Map<String, Need> needs, List<Flow> flows, Map<String, Timing> timing,
                        Map<String, ProductionRequirement> requiredInputs) {
        public Basis { needs = Map.copyOf(needs); flows = List.copyOf(flows); timing = Map.copyOf(timing); requiredInputs = Map.copyOf(requiredInputs); }
    }
    private SettlementReservePlanning() { }
    public static Map<ReserveSupplyPort.Key, ReserveSupplyPort> register(List<ReserveSupplyPort> ports) {
        var result = new EnumMap<ReserveSupplyPort.Key, ReserveSupplyPort>(ReserveSupplyPort.Key.class);
        for (var port : ports) if (result.put(port.key(), port) != null) throw new IllegalArgumentException("duplicate reserve facts owner");
        if (result.size() != ReserveSupplyPort.Key.values().length) throw new IllegalArgumentException("missing reserve facts owner");
        return Map.copyOf(result);
    }
    public static boolean enabled(FrontierWorldState state) { return state.bootstrap().ruleset().goodsTrade().supplyPlanning().reserves().isPresent(); }
    public static Map<String, Target> retainedTargets(FrontierWorldState state, SubjectId home) {
        var views = CACHE.get(List.of(state), HashMap::new);
        return views.computeIfAbsent(home, ignored -> project(state, home));
    }
    private static Map<String, Target> project(FrontierWorldState state, SubjectId home) {
        var rules = state.bootstrap().ruleset().goodsTrade().supplyPlanning();
        var policy = rules.reserves().orElseThrow(() -> new IllegalArgumentException("dynamic reserves not selected"));
        var basis = retainedBasis(state, home);
        return new SupplyReservePlanner().plan(basis.needs(), basis.flows(), basis.timing(), policy);
    }
    public static Basis retainedBasis(FrontierWorldState state, SubjectId home) {
        return BASIS.get(List.of(state), HashMap::new).computeIfAbsent(home, ignored -> collect(state, home, OptionalLong.empty()));
    }
    public static Basis basis(FrontierWorldState state, SubjectId home, long atTick) {
        if (atTick < 0) throw new IllegalArgumentException("negative reserve forecast instant");
        var cache = DATED.get(List.of(state), HashMap::new);
        var key = new InstantKey(home, atTick);
        var result = cache.get(key);
        if (result != null) return result;
        result = collect(state, home, OptionalLong.of(atTick));
        if (cache.size() >= 256) cache.clear();
        cache.put(key, result);
        return result;
    }
    public static Map<String, Target> read(FrontierWorldState state, SubjectId home, long atTick) {
        return targets(state, basis(state, home, atTick));
    }
    static Map<String, Target> targets(FrontierWorldState state, Basis basis) {
        var policy = state.bootstrap().ruleset().goodsTrade().supplyPlanning().reserves().orElseThrow();
        return new SupplyReservePlanner().plan(basis.needs(), basis.flows(), basis.timing(), policy);
    }
    private static Basis collect(FrontierWorldState state, SubjectId home, OptionalLong atTick) {
        var rules = state.bootstrap().ruleset().goodsTrade().supplyPlanning();
        var needs = new TreeMap<String, Need>(); var timing = new TreeMap<String, Timing>(); var flows = new ArrayList<Flow>();
        rules.commodities().forEach((item, rule) -> {
            needs.put(item, new Need(Rate.ZERO, 0)); timing.put(item, new Timing(rule.leadTicks(), TimingBasis.INITIAL_ESTIMATE));
        });
        var estimates = new HashMap<String, Timing>();
        var required = new TreeMap<String, ProductionRequirement>();
        for (var key : ReserveSupplyPort.Key.values()) {
            var facts = atTick.isPresent() ? PORTS.get(key).readAt(state, home, atTick.getAsLong()) : PORTS.get(key).read(state, home);
            if (!needs.keySet().containsAll(facts.needs().keySet()) || !needs.keySet().containsAll(facts.timing().keySet()))
                throw new IllegalArgumentException("reserve owner supplied undeclared resource");
            facts.needs().forEach((item, need) -> needs.merge(item, need,
                    (a, b) -> new Need(a.consumption().plus(b.consumption()), Math.addExact(a.immediate(), b.immediate()))));
            flows.addAll(facts.flows());
            facts.requiredInputs().forEach((item, requirement) -> required.merge(item, requirement,
                    ProductionRequirement::plus));
            facts.timing().forEach((item, estimate) -> estimates.merge(item, estimate,
                    (a, b) -> a.leadTicks() <= b.leadTicks() ? a : b));
        }
        timing.putAll(estimates);
        return new Basis(needs, flows, timing, required);
    }
}
