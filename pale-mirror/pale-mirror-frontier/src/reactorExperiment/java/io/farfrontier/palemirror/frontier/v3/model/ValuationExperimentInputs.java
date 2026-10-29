package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit;
import io.farfrontier.palemirror.frontier.v3.model.forestry.ForestKnowledge;
import java.util.*;

/** Offline read-set audit, NOT a production capability API or admission token. */
final class ValuationExperimentInputs {
    record Query(GoodsTradeParty party, GoodsPolicyKind policy, SubjectId home) {
        static Query of(GoodsParticipant p) { return new Query(p.party(), p.policy(), p.settlementId()); }
    }
    /** Bootstrap identity includes world/rules; distinct worlds cannot share a result. */
    static final class Epoch {
        final FrontierBootstrap bootstrap;
        Epoch(FrontierBootstrap bootstrap) { this.bootstrap = bootstrap; }
        @Override public boolean equals(Object other) { return other instanceof Epoch e && bootstrap == e.bootstrap; }
        @Override public int hashCode() { return System.identityHashCode(bootstrap); }
    }
    record Person(ResidentProfile profile, ActorLifeStatus life, boolean migrating) { }
    record Field(ResourceSiteLifecycle lifecycle, ResourceFieldCycle cycle, Set<ResourceFieldLayout.CellId> pendingCells) { }
    record Basis(Epoch epoch, Query query, Map<String, Integer> stock, Map<String, FixedScalar> references,
                 SettlementDailySchedule schedule, List<Person> people,
                 Map<ProductionStationSpec.Capability, List<ContainerRecord>> stations,
                 List<BlockPosition> stores, List<Field> fields, List<ExtractionDeposit> deposits,
                 Map<String, Long> forest) { }

    private final ImmutableInputView<Map<SubjectId, List<ExtractionDeposit>>> deposits = new ImmutableInputView<>();

    Basis capture(FrontierWorldState state, GoodsParticipant p) {
        var home = p.settlementId(); var rules = state.bootstrap().ruleset();
        var stock = new TreeMap<String, Integer>(); var prices = new TreeMap<String, FixedScalar>();
        for (var commodity : rules.goodsTrade().policies().get(p.policy())) {
            stock.put(commodity.itemKind(), GoodsParticipantStorage.unclaimed(state, p, commodity.itemKind()));
            prices.put(commodity.itemKind(), state.companies().goodsTrade().priceHistory()
                    .reference(commodity.itemKind(), rules.goodsTrade().pricing().recencyTicks()).orElse(commodity.minimumSellPrice()));
        }
        var people = state.humanPopulation().residentsIn(home).stream().map(r -> new Person(r,
                state.actorLocations().get(r.id()).condition().status(), state.humanPopulation().migrations().containsKey(r.id())))
                .sorted(Comparator.comparing(person -> person.profile().id())).toList();
        var stations = new EnumMap<ProductionStationSpec.Capability, List<ContainerRecord>>(ProductionStationSpec.Capability.class);
        for (var recipe : rules.production().recipes()) stations.computeIfAbsent(recipe.capability(),
                ignored -> AutomaticStationProduction.stations(state, home, recipe));
        var fields = new ArrayList<Field>();
        for (var id : state.resourceSites().siteIdsIn(home)) {
            var cycle = state.resourceSites().cycle(id); var pending = new HashSet<ResourceFieldLayout.CellId>();
            for (var cell : cycle.layout().cells()) if (state.resourceSites().hasPendingCellMutation(id, cell.id())) pending.add(cell.id());
            fields.add(new Field(state.resourceSites().site(id), cycle, Set.copyOf(pending)));
        }
        // Existing producer has no changed-home directory. One shared rebuild per changed
        // deposit root, NOT one scan per query. Count its cost, including initial population.
        var localDeposits = deposits.get(List.of(state.extractionSites().deposits()), () -> {
            var grouped = new HashMap<SubjectId, List<ExtractionDeposit>>();
            state.extractionSites().deposits().values().forEach(d -> grouped.computeIfAbsent(d.site().settlementId(),
                    ignored -> new ArrayList<>()).add(d));
            grouped.replaceAll((h, rows) -> rows.stream().sorted(Comparator.comparing(d -> d.site().id())).toList());
            return Map.copyOf(grouped);
        }).getOrDefault(home, List.of());
        var forest = new TreeMap<String, Long>();
        if (ForestryStockDemand.enabled(state, home)) for (var harvest : rules.forestry().harvests())
            forest.put(harvest.species(), ForestKnowledge.remaining(state, home, harvest.species()));
        var stores = FieldWorkForecast.storagePositions(state, home).stream().sorted(Comparator
                .comparingInt(BlockPosition::x).thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z)).toList();
        return new Basis(new Epoch(state.bootstrap()), Query.of(p), Map.copyOf(stock), Map.copyOf(prices),
                state.humanPopulation().schedule(home), people, Map.copyOf(stations), stores,
                List.copyOf(fields), localDeposits, Map.copyOf(forest));
    }
}
