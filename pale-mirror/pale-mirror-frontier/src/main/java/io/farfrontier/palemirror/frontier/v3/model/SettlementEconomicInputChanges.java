package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Closed fact-owner composition; no solver, scheduling or native effects. */
public final class SettlementEconomicInputChanges {
    private static final List<EconomicInputChangePort> PORTS = List.of(
            new InventoryEconomicInputChanges(), new MarketEconomicInputChanges(), new ProductionEconomicInputChanges(), new FieldEconomicInputChanges(),
            new PopulationEconomicInputChanges(), new LogisticsEconomicInputChanges());
    private static final class Changed {
        final Map<SettlementEconomicPlanningState.Change, Set<String>> affected = new EnumMap<>(SettlementEconomicPlanningState.Change.class);
    }
    public static FrontierWorldState retain(FrontierWorldState before, FrontierWorldState after) {
        var registry = after.companies().goodsTrade().participants();
        if (registry.participants().isEmpty() || !after.bootstrap().ruleset().goodsTrade().supplyPlanning().enabled()) return after;
        var previous = before.companies().goodsTrade().participants();
        var checked = registry;
        ImmutableMapChanges.forEachIdentityChangedKey(previous.planning(), checked.planning(), owner -> {
            var prior = previous.planning().get(owner);
            if (prior == null || !checked.participants().containsKey(owner)) return;
            var current = checked.economicState(owner);
            if (current.inputRevision() < prior.inputRevision()
                    || current.accepted().map(SettlementEconomicPlan::revision).orElse(0L)
                        < prior.accepted().map(SettlementEconomicPlan::revision).orElse(0L))
                throw new IllegalArgumentException("economic input/publication generation cannot be overwritten by a stale component");
        });
        var changes = new HashMap<SubjectId, Changed>();
        var registered = registry.participants().keySet();
        EconomicInputChangePort.Sink sink = (owner, reason, resources) -> {
            if (!registered.contains(owner)) return;
            var value = changes.computeIfAbsent(owner, ignored -> new Changed()); value.affected.merge(reason, Set.copyOf(resources), EconomicInputInvalidations::unionScopes);
        };
        for (var port : PORTS) port.collect(before, after, sink);
        if (changes.isEmpty()) return after;
        var replacements = new HashMap<SubjectId, SettlementEconomicPlanningState>();
        for (var entry : changes.entrySet()) replacements.put(entry.getKey(),
                registry.economicState(entry.getKey()).invalidate(entry.getValue().affected));
        registry = registry.withEconomicStates(replacements);
        return after.withCompanies(after.companies().withGoodsTrade(after.companies().goodsTrade().withParticipants(registry)));
    }
    private SettlementEconomicInputChanges() { }
}
