package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.BiFunction;

/** Declared production clients react to a replacement; the installation owner never inspects their jobs. */
public final class ProductionInstallationComposition {
    private static final Map<ProductionStationSpec.Capability,
            BiFunction<FrontierWorldState, ProductionStationSpec, FrontierWorldStateUpdate>> CLIENTS = Map.of(
            ProductionStationSpec.Capability.BAKING, BakeryStationInstallation::installed,
            ProductionStationSpec.Capability.SMELTING, (state, station) -> FrontierWorldStateUpdate.begin());
    static {
        if (!CLIENTS.keySet().equals(EnumSet.allOf(ProductionStationSpec.Capability.class)))
            throw new IllegalArgumentException("missing installation client capability");
    }
    public static FrontierWorldStateUpdate installed(FrontierWorldState state, ProductionStationSpec station) {
        return CLIENTS.get(station.capability()).apply(state, station);
    }
    private ProductionInstallationComposition() { }
}
