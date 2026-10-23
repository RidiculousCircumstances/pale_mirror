package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;
import java.util.Set;

/** Explicit work-permission/capability compatibility, not a recipe-to-worker classifier. */
public final class StationWorkAuthorization {
    private static final Map<ResidentWorkKind, Set<ProductionStationSpec.Capability>> CAPABILITIES = Map.of(
            ResidentWorkKind.PRODUCTION, Set.of(ProductionStationSpec.Capability.BAKING, ProductionStationSpec.Capability.FABRICATION));
    public static void require(ResidentWorkKind permission, ProductionStationSpec.Capability capability) {
        if (!CAPABILITIES.getOrDefault(permission, Set.of()).contains(capability))
            throw new IllegalArgumentException("station work declared an incompatible permission and capability");
    }
    private StationWorkAuthorization() { }
}
