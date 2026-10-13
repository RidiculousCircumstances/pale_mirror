package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;
import java.util.Set;

/** Closed compatibility registry for a declared machine capability and its owning production place. */
final class ProductionPlaces {
    private static final Map<ProductionStationSpec.Capability, Set<StructureKind>> PLACES = Map.of(
            ProductionStationSpec.Capability.BAKING, Set.of(StructureKind.WORKSHOP));
    static void require(ProductionStationSpec.Capability capability, SettlementStructure facility) {
        var allowed = PLACES.get(capability);
        if (allowed == null || !allowed.contains(facility.kind()))
            throw new IllegalArgumentException("declared production capability is incompatible with its place");
    }
    private ProductionPlaces() { }
}
