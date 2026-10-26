package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

final class ProductionCombustionFixtures {
    static FrontierWorldState igniteHotIfNeeded(FrontierWorldState state, SubjectId station, long tick) {
        var proposed = ProductionStationEnergy.next(state, station, tick);
        if (proposed.isEmpty()) return state;
        var burn = proposed.orElseThrow();
        if (burn.authorityEpoch() == 0) return ProductionStationEnergy.apply(state, station,
                new ProductionStationFuelChanged(ProductionStationFuelChanged.Stage.COLD_CONSUMED, burn, List.of(), tick));
        var remaining = state.inventory().fungibleResources().bindings().values().stream().filter(binding -> binding.accountId().equals(burn.accountId()))
                .map(binding -> new FungiblePhysicalObservation.Stack(binding.address(), binding.itemKind(), binding.quantity() -
                        (binding.address().equals(burn.source().getFirst().address()) ? 1 : 0))).toList();
        state = ProductionStationEnergy.apply(state, station, new ProductionStationFuelChanged(ProductionStationFuelChanged.Stage.HOT_PREPARED, burn, List.of(), tick));
        return ProductionStationEnergy.apply(state, station, new ProductionStationFuelChanged(ProductionStationFuelChanged.Stage.HOT_CONSUMED, burn, remaining, tick));
    }
    private ProductionCombustionFixtures() { }
}
