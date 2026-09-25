package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FungibleResourceCustodySupportTest {
    @Test
    void partialFieldYieldCombinesWithNextLotAndCannotIgnoreExistingClaims() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:lot-selection"), 91L));
        SubjectId owner = new SubjectId("settlement:1");
        SubjectId depot = FrontierWorldState.depotId(owner);
        SubjectId accountId = new SubjectId("custody:container-1-depot");
        SubjectId firstId = new SubjectId("lot:field-one-wheat");
        SubjectId secondId = new SubjectId("lot:field-two-wheat");
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        Map<SubjectId, ResourceLot> lots = new HashMap<>(resources.lots());
        lots.remove(new SubjectId("lot:bootstrap-1-wheat"));
        lots.put(firstId, new ResourceLot(firstId, owner, "minecraft:wheat", 63, "harvest:one", List.of()));
        lots.put(secondId, new ResourceLot(secondId, owner, "minecraft:wheat", 1, "harvest:two", List.of()));
        Map<SubjectId, CustodyAccount> accounts = new HashMap<>(resources.accounts());
        accounts.put(accountId, new CustodyAccount(accountId, new ResourceCustody.Container(depot),
                Map.of(firstId, 63, secondId, 1), Map.of()));
        resources = new FungibleResourceLedger(lots, resources.claims(), accounts, resources.bindings());
        state = state.withInventory(state.inventory().withFungibleResources(resources));

        var selected = FungibleResourceCustodySupport.selectAtContainer(state, depot, owner, "minecraft:wheat", 64).orElseThrow();
        assertEquals(accountId, selected.accountId());
        assertEquals(Map.of(firstId, 63, secondId, 1), selected.lotQuantities());
        assertEquals(firstId, selected.firstLotId());
        assertEquals(64, selected.quantity());
        assertTrue(FungibleResourceCustodySupport.firstAtContainer(state, depot, "minecraft:wheat", 64).isEmpty(),
                "the legacy single-lot query cannot admit this valid stock");

        ClaimAllocation claim = new ClaimAllocation(new SubjectId("claim:other-wheat"), new SubjectId("work:other"), owner,
                "minecraft:wheat", 32, Map.of(), ClaimPurpose.EXTERNAL_RESERVATION);
        state = state.withInventory(state.inventory().withFungibleResources(resources.reserve(claim, accountId)));
        assertTrue(FungibleResourceCustodySupport.selectAtContainer(state, depot, owner, "minecraft:wheat", 64).isEmpty());
    }
}
