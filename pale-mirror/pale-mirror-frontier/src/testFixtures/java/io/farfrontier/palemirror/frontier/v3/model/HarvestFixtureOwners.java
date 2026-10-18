package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Single-execution fixtures must refuse ambiguity; production never selects a preferred owner. */
public final class HarvestFixtureOwners {
    private HarvestFixtureOwners() { }

    /** Exact body-bound personal food, independent of a field job's crop hand. */
    public static FrontierWorldState withBoundPersonalFood(FrontierWorldState state, SubjectId actor, UUID body) {
        var account = new SubjectId("custody:release-personal-food");
        var lot = new SubjectId("lot:release-personal-food");
        var resources = state.inventory().fungibleResources().issue(new ResourceLot(lot,
                state.humanPopulation().resident(actor).settlementId(), "minecraft:bread", 2, "test:personal-food", List.of()),
                new CustodyAccount(account, new ResourceCustody.Actor(actor), Map.of(lot, 2), Map.of(),
                        Optional.of(new ActorItemSlot.Pocket(0))));
        resources = resources.rebind(account, 37L, FungiblePhysicalObservation.bind(resources, account, 37L,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorPocket(actor, body, 0),
                        "minecraft:bread", 2))));
        return state.withInventory(state.inventory().withFungibleResources(resources));
    }

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
