package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementFoodPolicyTest {
    @Test void anotherRegisteredOwnersBreadInPublicDepotIsNotPublicFood() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:foreign-food"), 408L));
        SubjectId buyer = state.bootstrap().settlements().getFirst().id(), seller = state.bootstrap().settlements().get(1).id();
        SubjectId depot = FrontierWorldState.depotId(buyer), account = ReferenceContainerCustody.scopeId(depot);
        SubjectId temporary = new SubjectId("custody:foreign-food"), lot = new SubjectId("lot:foreign-food");
        var resources = state.inventory().fungibleResources().issue(new ResourceLot(lot, seller, SettlementFoodPolicy.BREAD,
                64, "test-foreign-food", List.of()), new CustodyAccount(temporary,
                new ResourceCustody.WorldCarrier(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")), Map.of(lot, 64), Map.of()));
        resources = resources.transfer(temporary, account, Map.of(lot, 64), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        assertEquals(0, SettlementFoodPolicy.breadStock(state, buyer));
        assertEquals(0, SettlementFoodPolicy.reserveCoverageBread(state, buyer));
        assertEquals(0, SettlementFoodPolicy.coldUsableBread(state, buyer));
        assertTrue(SettlementFoodPolicy.exportableFungibleBread(state, buyer).isEmpty());
    }
    @Test void exactDepotReserveAllowsOneSurplusFungibleStackToExport() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:food-exact-reserve"), 408L));
        SubjectId settlement = initial.bootstrap().settlements().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement);
        int reserve = SettlementFoodPolicy.reserveRequirement(initial, settlement);
        SubjectId accountId = new SubjectId("custody:food-exact-reserve");
        SubjectId lotId = new SubjectId("lot:food-exact-reserve-surplus");
        FungibleResourceLedger original = initial.inventory().fungibleResources();
        CustodyAccount wheat = original.accounts().get(new SubjectId("custody:container-1-depot"));
        FungibleResourceLedger cleared = original.destroy(wheat.id(), wheat.lotQuantities(), Map.of());
        FungibleResourceLedger stocked = cleared.issue(new ResourceLot(lotId, settlement,
                SettlementFoodPolicy.BREAD, 64, "test-surplus", List.of()),
                new CustodyAccount(accountId, new ResourceCustody.Container(depot), Map.of(lotId, 64), Map.of()));
        ExactInventory inventory = initial.inventory().withFungibleResources(stocked);
        int left = reserve;
        for (int slot = 1; left > 0; slot++) {
            int count = Math.min(left, 64);
            inventory = inventory.store(new ExactItemStack(new SubjectId("item:exact-food-reserve-" + slot),
                    settlement, SettlementFoodPolicy.BREAD, count,
                    new InventoryCustody.ContainerSlot(depot, slot)));
            left -= count;
        }
        FrontierWorldState state = initial.withInventory(inventory);
        assertTrue(SettlementFoodPolicy.exportableFungibleBread(state, settlement).isPresent());
        assertEquals(lotId, SettlementFoodPolicy.exportableFungibleBread(state, settlement).orElseThrow().lot().id());
    }

    @Test void exportSelectsAnUnclaimedLotAndKeepsResidentReserve() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:food-claimed-export"), 407L));
        SubjectId settlement = state.bootstrap().settlements().getFirst().id();
        SubjectId depot = FrontierWorldState.depotId(settlement);
        FungibleResourceLedger original = state.inventory().fungibleResources();
        SubjectId wheatAccount = new SubjectId("custody:container-1-depot");
        CustodyAccount wheat = original.accounts().get(wheatAccount);
        FungibleResourceLedger cleared = original.destroy(wheatAccount, wheat.lotQuantities(), Map.of());

        SubjectId claimedLot = new SubjectId("lot:a-claimed-bread");
        SubjectId freeLot = new SubjectId("lot:z-free-bread");
        SubjectId accountId = new SubjectId("custody:food-export-test");
        Map<SubjectId, ResourceLot> lots = new HashMap<>(cleared.lots());
        lots.put(claimedLot, new ResourceLot(claimedLot, settlement, SettlementFoodPolicy.BREAD, 64,
                "test-bread", List.of()));
        lots.put(freeLot, new ResourceLot(freeLot, settlement, SettlementFoodPolicy.BREAD, 192,
                "test-bread", List.of()));
        Map<SubjectId, CustodyAccount> accounts = new HashMap<>(cleared.accounts());
        accounts.put(accountId, new CustodyAccount(accountId, new ResourceCustody.Container(depot),
                Map.of(claimedLot, 64, freeLot, 192), Map.of()));
        FungibleResourceLedger stocked = new FungibleResourceLedger(lots, cleared.claims(), accounts,
                cleared.bindings());
        SubjectId claimId = new SubjectId("claim:resident-bread-reserve");
        FungibleResourceLedger reserved = stocked.reserve(new ClaimAllocation(claimId,
                new SubjectId("resident:1-13"), settlement, SettlementFoodPolicy.BREAD, 64,
                Map.of(claimedLot, 64), ClaimPurpose.EXTERNAL_RESERVATION), accountId);
        state = state.withInventory(state.inventory().withFungibleResources(reserved));

        assertEquals(freeLot, SettlementFoodPolicy.exportableFungibleBread(state, settlement)
                .orElseThrow().lot().id());
        assertEquals(256, SettlementFoodPolicy.breadStock(state, settlement));
    }
}
