package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Public food policy chooses immediate food versus processable ingredients from current supply. */
public final class SettlementFoodPurchases {
    public record Advice(Map<String, Integer> limits, Map<String, Integer> protectedInputs) {
        public Advice { limits = Map.copyOf(limits); protectedInputs = Map.copyOf(protectedInputs); }
    }
    public static Advice assess(FrontierWorldState state, SubjectId home, int residents, Map<String, GoodsParticipantView.Stock> stocks) {
        var recipe = BakeryRecipes.bread(state);
        // The historic free recipe profiles retain their accepted market policy.
        if (recipe.heatPerBatch() == 0) return new Advice(Map.of(), Map.of());
        var food = stocks.get(recipe.product().itemKind());
        var commodity = state.bootstrap().ruleset().goodsTrade().policies().get(GoodsPolicyKind.PUBLIC_SETTLEMENT).stream()
                .filter(value -> value.itemKind().equals(recipe.product().itemKind())).findFirst().orElseThrow();
        long promisedFood = StockSupplyComposition.pending(state, StockSupplyPort.Key.BAKERY).stream()
                .filter(value -> value.economicOwner().equals(home) && value.itemKind().equals(recipe.product().itemKind()))
                .mapToLong(StockSupplyPort.Commitment::incoming).sum();
        long deficit = Math.max(0, StockTargetDemand.target(commodity, residents, food.protectedMinimum())
                - food.unclaimedInStorage() - food.expectedIncoming() - promisedFood);
        boolean urgent = SettlementFoodPolicy.reserveCoverageBread(state, home) < SettlementFoodPolicy.reserveRequirement(state, home);
        var catalog = state.bootstrap().ruleset().production();
        long heat = state.inventory().containers().values().stream().filter(record -> record.ownerId().equals(home)
                && record.productionStation().isPresent() && !ReferenceContainerCustody.blocksCanonicalUse(state, record.id())
                && state.productionStations().machines().containsKey(record.productionStation().orElseThrow().id()))
                .mapToLong(record -> state.productionStations().require(record.productionStation().orElseThrow().id()).storedHeat()).sum();
        for (var fuel : catalog.fuels()) {
            long stock = state.inventory().fungibleResources().accounts().values().stream()
                    .filter(account -> account.custody() instanceof ResourceCustody.Container container
                            && state.inventory().containers().get(container.containerId()).ownerId().equals(home))
                    .flatMap(account -> account.lotQuantities().entrySet().stream())
                    .filter(entry -> state.inventory().fungibleResources().lots().get(entry.getKey()).economicOwnerId().equals(home)
                            && state.inventory().fungibleResources().lots().get(entry.getKey()).itemKind().equals(fuel.itemKind()))
                    .mapToLong(Map.Entry::getValue).sum();
            heat = Math.addExact(heat, Math.multiplyExact(stock, fuel.heatUnits()));
        }
        var delay = BakeryProcessingForecast.firstOutputDelay(state, home);
        var life = state.bootstrap().ruleset().residentLife();
        long metabolism = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(home)
                        && state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE)
                .mapToLong(resident -> resident.characteristics().effectiveMetabolismPermille(
                        state.humanPopulation().nutrition(resident.id()).lastEvaluatedTick())).sum();
        long foodTime = metabolism == 0 ? Long.MAX_VALUE : Math.multiplyExact(Math.multiplyExact(
                (long) SettlementFoodPolicy.reserveCoverageBread(state, home), life.breadNutritionUnits()),
                Math.multiplyExact(life.satietyUnitTicks(), 1_000L)) / metabolism;
        boolean processing = heat >= recipe.heatPerBatch() && delay.isPresent() && delay.orElseThrow() <= foodTime;
        var result = new HashMap<String, Integer>();
        var protectedInputs = new HashMap<String, Integer>();
        // A critical shortage always admits bread. Otherwise usable local processing favours grain.
        if (!urgent && processing) result.put(recipe.product().itemKind(), 0);
        long needed = processing ? Math.multiplyExact(Math.ceilDiv(deficit, recipe.product().quantity()), recipe.ingredient().quantity()) : 0;
        for (var kind : recipe.ingredient().acceptedItems()) {
            var input = stocks.get(kind);
            if (input == null) throw new IllegalArgumentException("food recipe input has no registered public commodity");
            long fieldSupply = StockSupplyComposition.pending(state, StockSupplyPort.Key.FIELD).stream()
                    .filter(value -> value.economicOwner().equals(home) && value.itemKind().equals(kind))
                    .mapToLong(StockSupplyPort.Commitment::incoming).sum();
            long covered = input.unclaimedInStorage() + (long) input.expectedIncoming() + fieldSupply;
            result.put(kind, Math.toIntExact(Math.min(Integer.MAX_VALUE, Math.max(0, needed - covered))));
            protectedInputs.put(kind, Math.toIntExact(Math.min(4096, Math.max(0, needed - fieldSupply))));
        }
        return new Advice(result, protectedInputs);
    }
    private SettlementFoodPurchases() { }
}
