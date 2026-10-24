package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Closed fact-owner composition; no solver, scheduling or native effects. */
public final class SettlementEconomicInputChanges {
    private static final List<EconomicInputChangePort> PORTS = List.of(
            new InventoryEconomicInputChanges(), new MarketEconomicInputChanges(), new ProductionEconomicInputChanges(),
            new PopulationEconomicInputChanges(), new LogisticsEconomicInputChanges());
    private static final class Changed {
        final Map<SettlementEconomicPlanningState.Change, Set<String>> affected = new EnumMap<>(SettlementEconomicPlanningState.Change.class);
    }
    public static FrontierWorldState retain(FrontierWorldState before, FrontierWorldState after) {
        var registry = after.companies().goodsTrade().participants();
        if (registry.participants().isEmpty() || !after.bootstrap().ruleset().goodsTrade().supplyPlanning().enabled()) return after;
        var previous = before.companies().goodsTrade().participants();
        if (previous.planning() != registry.planning()) for (var entry : previous.planning().entrySet()) {
            if (!registry.participants().containsKey(entry.getKey())) continue;
            var current = registry.economicState(entry.getKey()); var prior = entry.getValue();
            if (current.inputRevision() < prior.inputRevision()
                    || current.accepted().map(SettlementEconomicPlan::revision).orElse(0L)
                        < prior.accepted().map(SettlementEconomicPlan::revision).orElse(0L))
                throw new IllegalArgumentException("economic input/publication generation cannot be overwritten by a stale component");
        }
        var changes = new HashMap<SubjectId, Changed>();
        var registered = registry.participants().keySet();
        EconomicInputChangePort.Sink sink = (owner, reason, resources) -> {
            if (!registered.contains(owner)) return;
            var value = changes.computeIfAbsent(owner, ignored -> new Changed()); value.affected.merge(reason, Set.copyOf(resources), (old, added) -> {
                if (old.isEmpty() || added.isEmpty()) return Set.of();
                var union = new HashSet<>(old); union.addAll(added); return Set.copyOf(union);
            });
        };
        for (var port : PORTS) port.collect(before, after, sink);
        if (changes.isEmpty()) return after;
        for (var entry : changes.entrySet()) registry = registry.withEconomicState(entry.getKey(),
                registry.economicState(entry.getKey()).invalidate(entry.getValue().affected));
        return after.withCompanies(after.companies().withGoodsTrade(after.companies().goodsTrade().withParticipants(registry)));
    }
    private SettlementEconomicInputChanges() { }
}
