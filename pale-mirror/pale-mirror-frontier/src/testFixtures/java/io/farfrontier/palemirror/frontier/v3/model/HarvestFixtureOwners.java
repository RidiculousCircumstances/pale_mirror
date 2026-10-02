package io.farfrontier.palemirror.frontier.v3.model;

/** Single-execution fixtures must refuse ambiguity; production never selects a preferred owner. */
public final class HarvestFixtureOwners {
    private HarvestFixtureOwners() { }

    public static <T> T rejectMultiple(T left, T right) {
        throw new IllegalStateException("single-owner fixture contains multiple executions; name an exact owner");
    }

    /** Declare a single participant for SDK boundary tests, not a production selection rule. */
    public static FrontierWorldState withSingleParticipant(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId settlement,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId resident) {
        var kinds = new java.util.EnumMap<ResidentWorkKind, java.util.Set<io.farfrontier.palemirror.frontier.v3.api.SubjectId>>(
                ResidentWorkKind.class);
        kinds.putAll(SettlementWorkPolicy.permissions(state, settlement).workers());
        kinds.put(ResidentWorkKind.AGRICULTURE, java.util.Set.of(resident));
        return state.withStrategicPlans(state.strategicPlans().withWorkPermissions(settlement,
                new ResidentWorkPermissions(kinds)));
    }
}
