package io.farfrontier.palemirror.frontier.v3.model;

/** Pure initial conditions, independent of house count, variant or nominal historical capacity. */
public final class DeclaredSettlementFixtures {
    public static FrontierWorldState displaceResidents(FrontierWorldState state, Settlement home) {
        for (var house : home.structures()) {
            if (house.kind() != StructureKind.HOUSING) continue;
            for (var bed : house.definition().beds().values()) {
                var head = house.placement().world(bed.head());
                state = StructureIntegrity.observe(state,
                        StructureObservationFixtures.missingCell(state, house, head, "fixture:bed-lost"));
            }
            if (SettlementFacilityCapability.housingCapacity(state, home.id())
                    < SettlementFacilityCapability.livingResidents(state, home.id())) return state;
        }
        throw new IllegalStateException("fixture could not create a housing deficit");
    }

    private DeclaredSettlementFixtures() {}
}
