package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Initial isolated-test stock placement; does not stand in for a native delivery receipt. */
public final class KitchenFoodFixture {
    private KitchenFoodFixture() { }
    public static FrontierWorldState relocateDepotFood(FrontierWorldState state, SubjectId home) {
        var source = FungibleResourceCustodySupport.accountAtContainer(state, FrontierWorldState.depotId(home)).orElse(null);
        if (source == null) return state;
        var pantry = CommunalDiningPlan.initialFoodStorage(state.bootstrap(), home);
        var ledger = state.inventory().fungibleResources();
        var lots = new TreeMap<SubjectId, Integer>();
        for (var entry : source.lotQuantities().entrySet())
            if (state.bootstrap().ruleset().residentLife().foods().foods().containsKey(ledger.lots().get(entry.getKey()).itemKind()))
                lots.put(entry.getKey(), entry.getValue());
        if (lots.isEmpty()) return state;
        var destination = FungibleResourceCustodySupport.accountAtContainer(state, pantry);
        ledger = destination.isPresent() ? ledger.transfer(source.id(), destination.orElseThrow().id(), lots, Map.of())
                : ledger.transferToNewAccount(source.id(), new CustodyAccount(ReferenceContainerCustody.scopeId(pantry),
                        new ResourceCustody.Container(pantry, ContainerSectionId.STOCK), lots, Map.of()));
        return state.withInventory(state.inventory().withFungibleResources(ledger));
    }
}
