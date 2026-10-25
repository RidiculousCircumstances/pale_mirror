package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.math.BigInteger;
import java.util.*;

/** Product policy chooses an equipment operation. Shared station work owns its actual fulfillment. */
public final class EquipmentProductionPlanning {
    public record Request(String pool, ProductionRecipe recipe, Optional<StationWorkState.MaintenanceTarget> maintenance,
                          boolean urgent, long usefulWear, long replacementCost, long workCostPerWear) { }
    public record Ready(Request request, SettlementStructure facility, FungibleResourceCustodySupport.LotSelection input) { }
    private record Key(SubjectId home, long tick) { }
    private static final ImmutableInputView<Map<Key, List<Request>>> REQUESTS = new ImmutableInputView<>();
    private static final ImmutableInputView<Map<Key, Acquisition>> PLANS = new ImmutableInputView<>();
    public static List<Request> requests(FrontierWorldState state, SubjectId home, long tick) {
        return REQUESTS.get(List.of(state), HashMap::new).computeIfAbsent(new Key(home, tick), ignored -> calculateRequests(state, home, tick));
    }
    private static List<Request> calculateRequests(FrontierWorldState state, SubjectId home, long tick) {
        var deficits = EquipmentStockProjection.deficits(state, home);
        if (deficits.isEmpty()) return List.of();
        var catalog = state.bootstrap().ruleset().production(); var tools = state.bootstrap().ruleset().tools();
        var reserved = WorkEquipmentClaimComposition.reserved(state); var result = new ArrayList<Request>();
        var market = EquipmentRecipeValuation.read(state, home, tick); var costs = market.processUnitCosts();
        for (var deficit : deficits) for (var recipe : catalog.recipes()) {
            var definition = tools.find(recipe.product().itemKind());
            if (definition.isEmpty() || !deficit.demand().supports(definition.orElseThrow())
                    || !costs.containsKey(recipe.id().value())) continue;
            long cost = costs.get(recipe.id().value());
            long workCost = Math.ceilDiv(Math.multiplyExact(Math.multiplyExact(deficit.demand().labourTicksPerWear(), 1000L),
                    state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().orElseThrow().labourValuePerTick()),
                    definition.orElseThrow().speedPermille());
            if (recipe.outcome() instanceof ProductionRecipeOutcome.Individual) {
                result.add(new Request(deficit.demand().pool(), recipe, Optional.empty(), deficit.immediate(),
                        definition.orElseThrow().durability() - definition.orElseThrow().maintenanceReserve(), cost, workCost));
            } else if (recipe.outcome() instanceof ProductionRecipeOutcome.Repair repair) {
                for (var item : ExactItemReadIndex.owned(state.inventory(), home)) {
                    if (!item.economicOwnerId().equals(home) || !item.itemKind().equals(recipe.product().itemKind())
                            || item.condition().damage() == 0 || reserved.contains(item.id())
                            || !(item.custody() instanceof InventoryCustody.ContainerSlot slot)
                            || !ResourceStorageSelection.containers(state, home, item.itemKind(), Set.of(ResourceStorageRole.WAREHOUSE, ResourceStorageRole.LOCAL_BUFFER))
                                .contains(slot.containerId())) continue;
                    int restored = Math.min(repair.restoredDurability(), item.condition().damage());
                    long before = deficit.demand().usableWear(definition.orElseThrow(), item.condition());
                    long after = deficit.demand().usableWear(definition.orElseThrow(), item.condition().restore(restored));
                    if (after <= before || deficit.immediate() && before > 0) continue;
                    result.add(new Request(deficit.demand().pool(), recipe, Optional.of(new StationWorkState.MaintenanceTarget(item.id(), slot.containerId(), item.itemKind())),
                            deficit.immediate(), after - before, cost, workCost));
                }
            }
        }
        var materialNow = new HashMap<ProductionRecipe.Id, Boolean>();
        var restart = new HashMap<ProductionRecipe.Id, EquipmentRecoveryFunding.Cost>();
        result.forEach(request -> materialNow.computeIfAbsent(request.recipe().id(),
                ignored -> materialAvailable(state, home, request.recipe())));
        result.forEach(request -> restart.computeIfAbsent(request.recipe().id(), ignored ->
                EquipmentRecoveryFunding.cost(state, home, request.recipe(), 1, market, tick)));
        return result.stream().distinct().sorted((a, b) -> {
            int urgent = Boolean.compare(b.urgent(), a.urgent()); if (urgent != 0) return urgent;
            int executable = Boolean.compare(materialNow.get(b.recipe().id()), materialNow.get(a.recipe().id()));
            if (executable != 0) return executable;
            var aStart = restart.get(a.recipe().id()); var bStart = restart.get(b.recipe().id());
            int funded = Boolean.compare(bStart.funded(market.availableMoney()), aStart.funded(market.availableMoney()));
            if (funded != 0) return funded;
            // An unquoted estimate is not obtainable supply, but an estimated
            // unaffordable upgrade must not suppress a cash-reachable maintenance
            // intention while the remaining equipment is still working.
            int cashReachable = Boolean.compare(bStart.cash() <= market.availableMoney(), aStart.cash() <= market.availableMoney());
            if (cashReachable != 0) return cashReachable;
            // Without an obtainable quoted bundle, restore productive service at
            // minimum expected cash before speculating on a fleet upgrade.
            if (!aStart.funded(market.availableMoney()) && !bStart.funded(market.availableMoney())) {
                int minimum = Long.compare(aStart.cash(), bStart.cash());
                if (minimum != 0) return minimum;
            }
            int value = BigInteger.valueOf(a.replacementCost()).add(BigInteger.valueOf(a.workCostPerWear()).multiply(BigInteger.valueOf(a.usefulWear())))
                    .multiply(BigInteger.valueOf(b.usefulWear())).compareTo(BigInteger.valueOf(b.replacementCost())
                        .add(BigInteger.valueOf(b.workCostPerWear()).multiply(BigInteger.valueOf(b.usefulWear()))).multiply(BigInteger.valueOf(a.usefulWear())));
            if (value != 0) return value;
            int recipe = a.recipe().id().value().compareTo(b.recipe().id().value()); if (recipe != 0) return recipe;
            return a.maintenance().map(t -> t.itemId().value()).orElse("").compareTo(b.maintenance().map(t -> t.itemId().value()).orElse(""));
        }).toList();
    }
    /** An immediate equipment shortage may use an executable alternative instead of waiting on theoretical imports. */
    private static boolean materialAvailable(FrontierWorldState state, SubjectId home, ProductionRecipe recipe) {
        return AutomaticStationProduction.stations(state, home, recipe).stream().anyMatch(record ->
                ProductionStationProcessing.canStart(state, record.productionStation().orElseThrow().id(), recipe.id(), recipe.ingredient().quantity()))
                && recipe.ingredient().acceptedItems().stream().anyMatch(kind -> ResourceStorageSelection.containers(state, home, kind,
                    Set.of(ResourceStorageRole.WAREHOUSE)).stream().anyMatch(container ->
                        FungibleResourceCustodySupport.selectAtContainer(state, container, home, kind, recipe.ingredient().quantity()).isPresent()));
    }
    public record Planned(Request request, int operations) { }
    public record Purchase(String pool, String itemKind, int quantity) { }
    public record Acquisition(List<Planned> production, List<Purchase> purchases) {
        public Acquisition { production = List.copyOf(production); purchases = List.copyOf(purchases); }
    }
    public static List<Planned> planned(FrontierWorldState state, SubjectId home, long tick) {
        return acquisition(state, home, tick).production();
    }
    /** One decision covers work pools, purchase funds, available offers and repair targets without double allocation. */
    public static Acquisition acquisition(FrontierWorldState state, SubjectId home, long tick) {
        return PLANS.get(List.of(state), HashMap::new).computeIfAbsent(new Key(home, tick), ignored -> calculatePlan(state, home, tick));
    }
    private static Acquisition calculatePlan(FrontierWorldState state, SubjectId home, long tick) {
        var deficits = EquipmentStockProjection.deficits(state, home);
        if (deficits.isEmpty()) return new Acquisition(List.of(), List.of());
        var requests = requests(state, home, tick); var result = new ArrayList<Planned>(); var purchases = new ArrayList<Purchase>();
        var used = new HashSet<SubjectId>(); var offerUsed = new HashMap<String, Integer>();
        var market = EquipmentRecipeValuation.read(state, home, tick); long money = market.availableMoney();
        var tools = state.bootstrap().ruleset().tools();
        for (var deficit : deficits) {
            int units = deficit.missingUnits(); long wear = deficit.missingWear();
            while (units > 0 || wear > 0) {
                var local = requests.stream().filter(r -> r.pool().equals(deficit.demand().pool())
                        && r.maintenance().filter(t -> used.contains(t.itemId())).isEmpty()).findFirst();
                io.farfrontier.palemirror.frontier.v3.model.supply.SourcingMarket.Offer chosen = null;
                long chosenWear = 0; long chosenValue = Long.MAX_VALUE;
                for (var offer : market.offers()) {
                    var definition = tools.find(offer.item());
                    if (definition.isEmpty() || !deficit.demand().supports(definition.orElseThrow())
                            || offerUsed.getOrDefault(offer.id(), 0) >= offer.quantity()
                            || Math.addExact(offer.cashPerItem(), offer.tripCashCost()) > money) continue;
                    var spec = GoodsStockSpecification.forKind(state.bootstrap().ruleset().goodsTrade(), offer.item());
                    if (spec.representation() != GoodsStockSpecification.Representation.INDIVIDUAL) continue;
                    long useful = Math.ceilDiv((long) definition.orElseThrow().durability() * spec.minimumRemainingPermille(), 1000)
                            - definition.orElseThrow().maintenanceReserve();
                    if (useful < 1) continue;
                    long work = Math.ceilDiv(Math.multiplyExact(Math.multiplyExact(deficit.demand().labourTicksPerWear(), 1000L),
                            state.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().orElseThrow().labourValuePerTick()),
                            definition.orElseThrow().speedPermille());
                    long value = Math.addExact(Math.addExact(offer.deliveredCostPerItem(), offer.tripCost()), Math.multiplyExact(work, useful));
                    if (chosen == null || ratioLess(value, useful, chosenValue, chosenWear, 0)) {
                        chosen = offer; chosenWear = useful; chosenValue = value;
                    }
                }
                boolean purchase = chosen != null && (local.isEmpty() ||
                        !(deficit.immediate() && materialAvailable(state, home, local.orElseThrow().recipe()))
                        && ratioLess(chosenValue, chosenWear, Math.addExact(local.orElseThrow().replacementCost(),
                            Math.multiplyExact(local.orElseThrow().workCostPerWear(), local.orElseThrow().usefulWear())),
                            local.orElseThrow().usefulWear(), market.switchingSavingsPermille()));
                if (purchase) {
                    purchases.add(new Purchase(deficit.demand().pool(), chosen.item(), 1));
                    offerUsed.merge(chosen.id(), 1, Math::addExact);
                    money -= Math.addExact(chosen.cashPerItem(), chosen.tripCashCost());
                    units = Math.max(0, units - 1); wear = Math.max(0, wear - chosenWear);
                } else if (local.isPresent()) {
                    var request = local.orElseThrow();
                    int quantity = request.maintenance().isPresent() ? 1 : Math.toIntExact(Math.max(units, Math.ceilDiv(wear, request.usefulWear())));
                    // Unquoted/unfunded fleet replacement must start with a finite
                    // obtainable unit before failure, too. Remaining wear stays in
                    // deficits; this intention is never an output promise.
                    while (quantity > 1 && !EquipmentRecoveryFunding.cost(state, home, request.recipe(), quantity, market, tick).funded(money)) quantity--;
                    result.add(new Planned(request, quantity)); request.maintenance().ifPresent(t -> used.add(t.itemId()));
                    units = Math.max(0, units - quantity); wear = Math.max(0, wear - Math.multiplyExact(quantity, request.usefulWear()));
                    var start = EquipmentRecoveryFunding.cost(state, home, request.recipe(), quantity, market, tick);
                    if (!start.funded(money)) break;
                    money -= start.cash();
                } else break; // Neither production capability nor a funded delivered alternative exists.
            }
        }
        return new Acquisition(result, purchases);
    }
    private static boolean ratioLess(long cost, long wear, long otherCost, long otherWear, int savings) {
        return BigInteger.valueOf(cost).multiply(BigInteger.valueOf(otherWear)).multiply(BigInteger.valueOf(1000))
                .compareTo(BigInteger.valueOf(otherCost).multiply(BigInteger.valueOf(wear)).multiply(BigInteger.valueOf(1000L - savings))) < 0;
    }
    /** Stock-only availability; route calculations belong to exact order admission. */
    public static Optional<Ready> ready(FrontierWorldState state, SubjectId home, long tick) {
        for (var plan : planned(state, home, tick)) {
            var request = plan.request();
            var recipe = request.recipe();
            for (var record : AutomaticStationProduction.stations(state, home, recipe)) {
                var station = record.productionStation().orElseThrow();
                if (!ProductionFacilityReservations.available(state.productionJobs(), station.facilityId())
                        || !ProductionStationProcessing.canStart(state, station.id(), recipe.id(), recipe.ingredient().quantity())) continue;
                var facility = FrontierWorldStateSupport.settlement(state.bootstrap(), home).structures().stream()
                        .filter(value -> value.id().equals(station.facilityId())).findFirst().orElseThrow();
                for (var kind : recipe.ingredient().acceptedItems().stream().sorted().toList())
                    for (var container : ResourceStorageSelection.containers(state, home, kind, Set.of(ResourceStorageRole.WAREHOUSE)).stream().sorted().toList()) {
                        var selected = FungibleResourceCustodySupport.selectAtContainer(state, container, home, kind, recipe.ingredient().quantity());
                        if (selected.isEmpty() || !FungibleResourceCustodySupport.canReserve(state, selected.orElseThrow())
                                || ProductionOutputCapacity.selectRecipeDestination(state, ProductionRights.publicService(home), recipe,
                                    recipe.ingredient().quantity(), container, SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach(),
                                    request.maintenance()).isEmpty()) continue;
                        return Optional.of(new Ready(request, facility, selected.orElseThrow()));
                    }
            }
        }
        return Optional.empty();
    }
    private EquipmentProductionPlanning() { }
}
