package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Regression for ordinary menu withdrawals, not player-slot attributed physical handoffs. */
class GoodsTradePlayerStockLossTest {
    @Test void witnessedPartialAndWholeStackExitDisposesTradeAndRecoversWithoutDeliveryOrPayment() {
        for (int departed : List.of(32, 64)) {
            var state = GoodsTradeTest.reserved(); var shipment = GoodsTradeTest.shipment(state);
            var owner = shipment.sender().settlementId(); var account = shipment.sourceAccountId();
            var depot = ResourceSourceComposition.requireContainer(shipment.sender()).containerId();
            var claim = shipment.authorization().claimId();
            var contract = state.companies().goodsTrade().contracts().get(shipment.authorization().claimantId());
            state = GoodsTradeTest.factForFamily(state, owner, new ShipmentDispatched(shipment), 0, "shipments");
            var sourceSlot = state.inventory().availableSlots(depot).getFirst();
            var resources = state.inventory().fungibleResources();
            var stacks = List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                    new InventoryCustody.ContainerSlot(depot, sourceSlot)), "minecraft:wheat", 64));
            resources = resources.rebind(account, 1, FungiblePhysicalObservation.bind(resources, account, 1, stacks));
            state = ReferenceContainerCustodyFixtures.observedAndHeld(state.withInventory(state.inventory()
                    .withFungibleResources(resources).withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                    .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)), depot);
            var before = state;
            assertTrue(SourceStockClaimLoss.admissibleClaims(before, account).contains(claim));
            assertTrue(SourceStockClaimLoss.canDepart(before, account, shipment.itemKind(), departed));
            assertFalse(SourceStockClaimLoss.canDepart(before, account, shipment.itemKind(), 65));
            var remaining = departed == 64 ? List.<FungiblePhysicalObservation.Stack>of()
                    : List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(depot, sourceSlot)), shipment.itemKind(), 64 - departed));
            var loss = assertInstanceOf(FungibleStockDepartureObserved.class, FungibleContainerPlayerEdit.classify(
                    resources, account, depot, owner, 1, UUID.randomUUID(), UUID.randomUUID(), remaining,
                    SourceStockClaimLoss.admissibleClaims(before, account)));
            var result = GoodsTradeTest.factForFamily(before, owner, loss, 300, "physical-observation");
            assertEquals(64 - departed, result.inventory().fungibleResources().totalQuantity(owner, shipment.itemKind()));
            assertTrue(result.companies().goodsTrade().contracts().get(contract.id()).terminal());
            assertFalse(result.companies().goodsTrade().contracts().get(contract.id()).fulfilled());
            assertEquals(before.inventory().economics().accounts(), result.inventory().economics().accounts());
            assertFalse(result.inventory().economics().reservations().containsKey(contract.financialReservationId()));
            assertEquals(Shipment.Status.ALLOCATION_WITHDRAWN, result.shipments().shipments().get(shipment.id()).status());
            assertTrue(result.actorExecutions().actors().get(shipment.execution().actorId()).current().isEmpty());
            assertEquals(1, SourceStockClaimLoss.released(before, loss).size());
            var recovered = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(result));
            assertEquals(result, recovered);
            assertThrows(IllegalArgumentException.class,
                    () -> GoodsTradeTest.factForFamily(recovered, owner, loss, 301, "physical-observation"));
        }
    }

    @Test void unrelatedUnsupportedReservationDoesNotFenceTheClickedResource() {
        var state = GoodsTradeTest.reserved(); var shipment = GoodsTradeTest.shipment(state);
        var owner = shipment.sender().settlementId(); var account = shipment.sourceAccountId();
        var lot = new SubjectId("lot:other-held-resource"); var claim = new SubjectId("claim:other-held-resource");
        var staging = new SubjectId("custody:other-held-resource");
        var resources = state.inventory().fungibleResources().issue(
                new ResourceLot(lot, owner, "minecraft:cobblestone", 1, "test:unrelated-reservation", List.of()),
                new CustodyAccount(staging, shipment.sender().custody(), Map.of(lot, 1), Map.of()))
                .transfer(staging, account, Map.of(lot, 1), Map.of())
                .reserve(new ClaimAllocation(claim, owner, owner, "minecraft:cobblestone", 1,
                        Map.of(lot, 1), ClaimPurpose.EXTERNAL_RESERVATION), account);
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        assertFalse(SourceStockClaimLoss.admissibleClaims(state, account).contains(claim));
        assertTrue(SourceStockClaimLoss.canDepart(state, account, shipment.itemKind(), 64));
        assertFalse(SourceStockClaimLoss.canDepart(state, account, "minecraft:cobblestone", 1));
    }
}
