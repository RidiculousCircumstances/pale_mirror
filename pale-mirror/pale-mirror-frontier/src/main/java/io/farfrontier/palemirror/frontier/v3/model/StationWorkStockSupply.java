package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Recipe owner alone translates its retained batch into future supply and consumption. */
final class StationWorkStockSupply implements StockSupplyPort {
    @Override public Key key() { return Key.STATION_WORK; }
    @Override public long inputDemand(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId owner, String itemKind, long atTick) {
        if (state.bootstrap().ruleset().goodsTrade().supplyPlanning().enabled()) {
            var participant = state.companies().goodsTrade().participants().participants().get(owner);
            var plan = GoodsParticipantAssessment.domestic(state, participant, atTick).view().supplyPlan().orElseThrow();
            return plan.require(itemKind).protectedInput();
        }
        var recipe = BakeryRecipes.bread(state);
        return recipe.ingredient().accepts(itemKind) ? Math.multiplyExact(
                Math.ceilDiv(ProductionStockDemand.readSupply(state, owner, recipe.product().itemKind(), Optional.empty(), atTick).missing(),
                        recipe.product().quantity()), recipe.ingredient().quantity()) : 0;
    }
    @Override public List<Commitment> pending(FrontierWorldState state) {
        var result = new ArrayList<Commitment>();
        var ledger = state.inventory().fungibleResources();
        for (var job : state.productionJobs().values()) {
            // Once the recipe issued its exact output, the inventory projection counts it.
            boolean outputExists = job.stationWork().isPresent() && StationWorkCargo.output(job)
                    || job.stationWork().isEmpty() && state.inventory().items().containsKey(job.outputItemId())
                    || ledger.lots().containsKey(job.outputItemId());
            if (outputExists) continue;
            var owner = job.rights().resourceOwner().id();
            if (job.stationWork().flatMap(StationWorkState::maintenanceTarget).isEmpty())
                result.add(new Commitment(job.id(), owner, job.outputItemKind(), job.outputCount(), 0));
            long input = switch (job.inputHold()) {
                case ProductionInputHold.Cold ignored -> 0; // Removed from inventory at admission already.
                case ProductionInputHold.Materialized exact -> Optional.ofNullable(state.inventory().items().get(exact.itemId()))
                        .map(ExactItemStack::count).orElse(0);
                case ProductionInputHold.FungibleCold cold -> cold.inputLots().values().stream().mapToLong(Integer::longValue).sum();
                case ProductionInputHold.FungibleBound bound -> bound.inputLots().values().stream().mapToLong(Integer::longValue).sum();
            };
            result.add(new Commitment(job.id(), owner, job.stationWork().map(StationWorkState::inputItemKind).orElse("minecraft:wheat"), 0, input));
        }
        return List.copyOf(result);
    }
}
