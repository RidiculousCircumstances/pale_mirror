package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import java.util.*;

/** Nutrition selects the current staple once; substitutes are not independent appetites. */
final class NutritionReserveSupply implements ReserveSupplyPort {
    @Override public Key key() { return Key.NUTRITION; }
    @Override public Facts read(FrontierWorldState state, SubjectId home) {
        var life = state.bootstrap().ruleset().residentLife();
        long metabolism = 0, immediate = 0;
        for (var resident : state.humanPopulation().residents().values()) {
            if (!resident.settlementId().equals(home) || state.actorLocations().get(resident.id()).condition().status() != ActorLifeStatus.ALIVE) continue;
            var nutrition = state.humanPopulation().nutrition(resident.id());
            metabolism = Math.addExact(metabolism, resident.characteristics().effectiveMetabolismPermille(nutrition.lastEvaluatedTick()));
            immediate = Math.addExact(immediate, Math.ceilDiv(nutrition.nutritionWanted(life), life.breadNutritionUnits()));
        }
        long period = Math.multiplyExact(Math.multiplyExact(life.satietyUnitTicks(), 1000L), life.breadNutritionUnits());
        return new Facts(Map.of(SettlementFoodPolicy.BREAD, new Need(Rate.of(metabolism, period), immediate)), List.of(), Map.of());
    }
}
