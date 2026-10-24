package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionProvisioning;
import io.farfrontier.palemirror.frontier.v3.model.market.DeliveryCost;
import java.util.*;

/** Shared commercial valuation of provisioning facts. No cash, cargo or route authority. */
public final class GoodsTransportValuation {
    private GoodsTransportValuation() { }
    public static DeliveryCost estimate(FrontierWorldState state, GoodsParticipant organizer, long duration, int workers, long now) {
        var life = state.bootstrap().ruleset().residentLife();
        var food = life.foods().foods().values().stream().min(Comparator.comparing(FoodCatalog.Food::itemKind)).orElseThrow();
        int items = ExpeditionProvisioning.requiredFoodItems(duration, life.mealTargetUnits(), life.metabolismDefaultPermille(), food, life);
        return new DeliveryCost(GoodsResourceValuation.replacement(state, organizer, food.itemKind(), now).multiply(Math.multiplyExact(items, workers)),
                Math.multiplyExact(duration, workers));
    }
    public static DeliveryCost exact(FrontierWorldState state, GoodsParticipant organizer, TransportMission mission, long now) {
        var load = mission.supplies().orElseThrow();
        var life = state.bootstrap().ruleset().residentLife();
        var food = life.foods().foods().get(load.foodKind());
        int items = 0;
        for (var actor : load.foodTargets().keySet()) {
            var resident = Objects.requireNonNull(state.humanPopulation().residents().get(actor), "declared provisioned person");
            items = Math.addExact(items, ExpeditionProvisioning.requiredFoodItems(load.forecastDurationTicks(),
                    life.mealTargetUnits(), resident.characteristics().effectiveMetabolismPermille(now), food, life));
        }
        return new DeliveryCost(GoodsResourceValuation.replacement(state, organizer, load.foodKind(), now).multiply(items),
                Math.multiplyExact(load.forecastDurationTicks(), load.foodTargets().size()));
    }
}
