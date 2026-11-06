package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.supply.SupplyReservePlanner.*;
import java.util.*;

/** Sustained recipe demand survives an occupied station or a baker's meal; a destroyed machine does not. */
final class BakeryReserveSupply implements ReserveSupplyPort {
    @Override public Key key() { return Key.BAKERY; }
    @Override public Facts read(FrontierWorldState state, SubjectId home) {
        var recipe = BakeryRecipes.bread(state); var catalog = state.bootstrap().ruleset().production();
        boolean installed = state.inventory().containers().values().stream().filter(c -> c.ownerId().equals(home))
                .filter(c -> state.inventory().surfaces().get(c.id()).status() != ContainerSurfaceStatus.RETIRED
                        && state.inventory().surfaces().get(c.id()).status() != ContainerSurfaceStatus.CONFLICT)
                .flatMap(c -> c.productionStation().stream()).anyMatch(station -> station.capability() == recipe.capability()
                        && FacilityFunctionAvailability.mayUse(state, station.facilityId()));
        if (!installed) return new Facts(Map.of(), List.of(), Map.of());
        var inputs = new TreeMap<String, Rate>();
        // One explicitly selected alternative. The processing owner can replace this selection policy.
        String ingredient = recipe.singleIngredient().acceptedItems().stream().sorted().findFirst().orElseThrow();
        inputs.put(ingredient, Rate.of(recipe.singleIngredient().quantity(), recipe.product().quantity()));
        if (recipe.nominalBurnTicksPerBatch(state.bootstrap().ruleset().workCatalog()) > 0) {
            var fuel = catalog.fuels().stream().sorted(Comparator.comparingLong(ProductionCatalog.Fuel::burnTicks).reversed()
                    .thenComparing(ProductionCatalog.Fuel::itemKind)).findFirst().orElseThrow();
            inputs.merge(fuel.itemKind(), Rate.of(recipe.nominalBurnTicksPerBatch(state.bootstrap().ruleset().workCatalog()), Math.multiplyExact(fuel.burnTicks(), recipe.product().quantity())), Rate::plus);
        }
        long lead = BakeryProcessingForecast.reserveLeadTicks(state, home);
        return new Facts(Map.of(), List.of(new Flow(recipe.product().itemKind(), inputs)),
                Map.of(recipe.product().itemKind(), new Timing(Math.max(1, lead), TimingBasis.LOCAL_ESTIMATE)));
    }
}
