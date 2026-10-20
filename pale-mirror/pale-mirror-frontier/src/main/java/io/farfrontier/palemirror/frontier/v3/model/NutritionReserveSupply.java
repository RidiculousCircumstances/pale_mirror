package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import java.util.*;

/** Nutrition selects the current staple once; substitutes are not independent appetites. */
final class NutritionReserveSupply implements ReserveSupplyPort {
    @Override public Key key() { return Key.NUTRITION; }
    @Override public Facts read(FrontierWorldState state, SubjectId home) {
        return facts(state, home, OptionalLong.empty());
    }
    @Override public Facts readAt(FrontierWorldState state, SubjectId home, long atTick) {
        if (atTick < 0) throw new IllegalArgumentException("negative nutrition forecast instant");
        return facts(state, home, OptionalLong.of(atTick));
    }
    private Facts facts(FrontierWorldState state, SubjectId home, OptionalLong atTick) {
        var life = state.bootstrap().ruleset().residentLife();
        long metabolism = 0, immediate = 0;
        for (var resident : state.humanPopulation().residents().values()) {
            if (!resident.settlementId().equals(home) || state.actorLocations().get(resident.id()).condition().status() != ActorLifeStatus.ALIVE) continue;
            var nutrition = state.humanPopulation().nutrition(resident.id());
            long instant = atTick.orElse(nutrition.lastEvaluatedTick());
            int rate = resident.characteristics().effectiveMetabolismPermille(instant);
            if (atTick.isPresent()) nutrition = nutrition.accrueThrough(instant, life, rate);
            metabolism = Math.addExact(metabolism, rate);
            immediate = Math.addExact(immediate, Math.ceilDiv(nutrition.nutritionWanted(life), life.breadNutritionUnits()));
        }
        var yield = FoodConsumptionForecast.atThreshold(life.foods().require(SettlementFoodPolicy.BREAD), life);
        long period = Math.multiplyExact(Math.multiplyExact(life.satietyUnitTicks(), 1000L), yield.usefulNutrition());
        return new Facts(Map.of(SettlementFoodPolicy.BREAD,
                new Need(Rate.of(Math.multiplyExact(metabolism, yield.items()), period), immediate)), List.of(), Map.of());
    }
}
