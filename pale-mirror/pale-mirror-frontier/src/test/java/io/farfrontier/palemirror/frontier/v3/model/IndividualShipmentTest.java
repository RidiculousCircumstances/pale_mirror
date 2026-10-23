package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Declared-contract fixture isolates custody/payment and recovery; not route or market-policy evidence. */
class IndividualShipmentTest {
    @Test void deliveryPreservesIdentityAndWearAndPaysOnlyOnAcceptanceAcrossRecovery() {
        var f = fixture(); var state = dispatch(f); var shipment = f.shipment();
        long balance = state.inventory().economics().require(f.contract().buyer().id()).balance().raw();
        state = state.withActorBody(shipment.execution().actorId(), shipment.sender().station().standingBody());
        state = transfer(state, shipment.id());
        assertEquals(new InventoryCustody.Actor(shipment.execution().actorId()), state.inventory().items().get(f.item().id()).custody());
        assertEquals(f.item().economicOwnerId(), state.inventory().items().get(f.item().id()).economicOwnerId());
        state = restored(state);
        state = state.withActorBody(shipment.execution().actorId(), shipment.receiver().station().standingBody());
        state = transfer(state, shipment.id());
        var delivered = state.shipments().shipments().get(shipment.id());
        assertEquals(Shipment.Status.DELIVERED, delivered.status());
        assertEquals(f.item().condition(), state.inventory().items().get(f.item().id()).condition());
        assertEquals(f.item().economicOwnerId(), state.inventory().items().get(f.item().id()).economicOwnerId());
        assertEquals(balance, state.inventory().economics().require(f.contract().buyer().id()).balance().raw());
        var reception = delivered.reception().orElseThrow();
        var receipt = new GoodsTradeAcceptance(reception.id(), f.contract().id(), 0,
                GoodsShipmentTitle.received(state, delivered, f.contract(), reception.id(), reception.claimId(), reception.lotQuantities(), false));
        state = GoodsTradeTest.fact(state, f.contract().buyer().id(), new GoodsTradeAccepted(receipt));
        assertEquals(f.contract().buyer().id(), state.inventory().items().get(f.item().id()).economicOwnerId());
        assertEquals(balance - f.contract().deliveredUnitPrice().raw(), state.inventory().economics().require(f.contract().buyer().id()).balance().raw());
        var accepted = state;
        assertThrows(IllegalArgumentException.class, () -> GoodsTradeTest.fact(accepted, f.contract().buyer().id(), new GoodsTradeAccepted(receipt)));
        state = fact(state, shipment.id(), new ShipmentReceiptAcknowledged(shipment.id(), delivered.revision(), reception.id()));
        assertFalse(WorkEquipmentClaimComposition.reserved(state).contains(f.item().id()));
        state = restored(state);
        assertEquals(f.item().condition(), state.inventory().items().get(f.item().id()).condition());
    }
    @Test void preparedHotTransferRetainsItsExactPreimageAcrossRecoveryAndRejectsReplay() {
        var f = fixture(); var state = dispatch(f); var shipment = f.shipment(); var actor = shipment.execution().actorId();
        state = state.withActorBody(actor, shipment.sender().station().standingBody());
        state = ModeledActorBodyFacts.present(state, actor);
        var lease = new AmbientActorLease(actor, state.actorLocations().get(actor).body(), new SimInstant(0), 1,
                AmbientLeaseStatus.HOT, AmbientGoalKind.WORK, state.actorLocations().get(actor).body());
        state = state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(Map.of(actor, lease)));
        state = ReferenceContainerCustodyFixtures.observedAndHeld(state, shipment.sender().container().orElseThrow());
        var identity = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(ActorBodyAuthority.current(state, actor), shipment.execution());
        var order = (ActorContainerItemOrder) shipment.itemOrder(state);
        var witness = IndividualItemTransferWitness.capture(state, order, identity.body().physicalEpoch());
        var step = new ShipmentPhysicalStep(shipment.status(), shipment.revision(),
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation(identity, lease.revision()),
                List.of(), -1, identity.body().physicalEpoch(), shipment.portions(), 0, Optional.of(witness));
        var receipt = new ShipmentHotTransferred(shipment.id(), step, List.of(), List.of());
        var before = state;
        assertThrows(IllegalArgumentException.class, () -> ShipmentConfirmationFixture.apply(before, receipt));
        state = ShipmentConfirmationFixture.apply(state, new ShipmentHotPrepared(shipment.id(), step));
        state = restored(state);
        state = ShipmentConfirmationFixture.apply(state, receipt);
        assertEquals(new InventoryCustody.Actor(actor), state.inventory().items().get(f.item().id()).custody());
        assertEquals(f.item().condition(), state.inventory().items().get(f.item().id()).condition());
        assertEquals(f.contract().seller().id(), state.inventory().items().get(f.item().id()).economicOwnerId());
        var after = restored(state);
        assertThrows(IllegalArgumentException.class, () -> ShipmentConfirmationFixture.apply(after, receipt));
    }
    @Test void rearrangingSourceOrUnacceptedDestinationRetainsTitleAndPromise() {
        for (boolean unloaded : List.of(false, true)) {
            var f = fixture(); var state = dispatch(f); var shipment = f.shipment();
            if (unloaded) {
                state = transfer(state.withActorBody(shipment.execution().actorId(), shipment.sender().station().standingBody()), shipment.id());
                state = transfer(state.withActorBody(shipment.execution().actorId(), shipment.receiver().station().standingBody()), shipment.id());
            }
            var item = state.inventory().items().get(f.item().id()); var from = (InventoryCustody.ContainerSlot) item.custody();
            var to = new InventoryCustody.ContainerSlot(from.containerId(), state.inventory().availableSlots(from.containerId()).getLast());
            var after = GoodsTradeTest.factForFamily(state, item.economicOwnerId(), new ExactItemCustodyChanged(item.id(), from, to), 1, "physical-observation");
            assertEquals(state.companies().goodsTrade(), after.companies().goodsTrade());
            assertEquals(state.shipments(), after.shipments());
            assertEquals(item.withCustody(to), after.inventory().items().get(item.id()));
            restored(after);
        }
    }
    @Test void externalRemovalClosesSaleAndTransportWithoutDestroyingTheItem() {
        for (boolean unloaded : List.of(false, true)) {
            var f = fixture(); var state = dispatch(f); var shipment = f.shipment();
            if (unloaded) {
                state = transfer(state.withActorBody(shipment.execution().actorId(), shipment.sender().station().standingBody()), shipment.id());
                state = transfer(state.withActorBody(shipment.execution().actorId(), shipment.receiver().station().standingBody()), shipment.id());
            }
            var item = state.inventory().items().get(f.item().id());
            var player = new InventoryCustody.Player(new UUID(0, 228));
            var observation = new ExactItemCustodyChanged(item.id(), item.custody(), player);
            state = GoodsTradeTest.factForFamily(state, item.economicOwnerId(), observation, 1, "physical-observation");
            assertEquals(player, state.inventory().items().get(item.id()).custody());
            assertEquals(item.condition(), state.inventory().items().get(item.id()).condition());
            assertTrue(state.companies().goodsTrade().contracts().get(f.contract().id()).terminal());
            assertFalse(state.inventory().economics().reservations().containsKey(f.contract().financialReservationId()));
            assertTrue(state.shipments().shipments().get(shipment.id()).terminal());
            assertTrue(state.shipments().shipments().get(shipment.id()).reception().isEmpty());
            restored(state);
        }
    }
    private record Fixture(FrontierWorldState state, Shipment shipment, GoodsTradeContract contract, ExactItemStack item) { }
    private static Fixture fixture() {
        var state = GoodsTradeTest.initial(); var route = GoodsTradeTest.shipment(state);
        var seller = state.companies().goodsTrade().participants().participants().get(route.sender().settlementId()).party();
        var buyer = state.companies().goodsTrade().participants().participants().get(route.receiver().settlementId()).party();
        var itemId = new SubjectId("item:individual-sale"); var contractId = new SubjectId("contract:individual-sale"); var claimId = new SubjectId("claim:individual-sale");
        var source = route.sender().container().orElseThrow(); var destination = route.receiver().containerId();
        int slot = state.inventory().availableSlots(source, state.reservedContainerSlots(source)).getFirst();
        var item = new ExactItemStack(itemId, seller.id(), "minecraft:iron_pickaxe", 1,
                new InventoryCustody.ContainerSlot(source, slot), new ItemCondition(250, 17));
        var items = new HashMap<>(state.inventory().items()); items.put(itemId, item);
        var inventory = state.inventory();
        state = state.withInventory(new ExactInventory(inventory.containers(), items, inventory.cargo(), inventory.playerItems(), inventory.worldCarrierItems(),
                inventory.conflicts(), inventory.surfaces(), inventory.economics(), inventory.fungibleResources()));
        var sell = new GoodsTradeOrder(new SubjectId("order:individual-sell"), seller, buyer, GoodsTradeOrder.Side.SELL, source, item.itemKind(), 1, 0, FixedScalar.ONE, 10_000);
        var buy = new GoodsTradeOrder(new SubjectId("order:individual-buy"), buyer, seller, GoodsTradeOrder.Side.BUY, destination, item.itemKind(), 1, 0, FixedScalar.ONE, 10_000);
        var allocation = new IndividualResourceAllocation(claimId, contractId, seller.id(), itemId, item.itemKind(), item.condition());
        var contract = new GoodsTradeContract(contractId, sell.id(), buy.id(), seller, buyer, new GoodsTradeFulfillment(GoodsTradeFulfillment.Mode.SELLER_DELIVERY, seller),
                source, destination, item.itemKind(), 1, FixedScalar.ONE, new SubjectId("reservation:individual-sale"), 0, Map.of(claimId, 1), Map.of(), Map.of(),
                0, 0, FixedScalar.ZERO, Map.of(claimId, allocation));
        // Initial retained terms and funded reservation, not a simulated market match or delivery.
        var trade = state.companies().goodsTrade().place(sell).place(buy).admit(contract, 0);
        state = state.withChanges(FrontierWorldStateUpdate.begin().companies(state.companies().withGoodsTrade(trade))
                .inventory(state.inventory().withEconomics(state.inventory().economics().reserve(new FinancialReservation(
                    contract.financialReservationId(), buyer.id(), seller.id(), contract.id(), contract.outstandingPrice())))));
        var shipment = new Shipment(route.id(), new ResourceClaimDelegation(ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT,
                claimId, contractId, route.id(), 0), route.execution(), route.sender(), route.receiver(), new ShipmentCargo.Individual(allocation),
                Shipment.Status.AWAITING_LOAD, 1, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        return new Fixture(state, shipment, contract, item);
    }
    private static FrontierWorldState dispatch(Fixture f) { return fact(f.state(), f.contract().seller().id(), new ShipmentDispatched(f.shipment())); }
    private static FrontierWorldState transfer(FrontierWorldState state, SubjectId id) {
        var shipment = state.shipments().shipments().get(id);
        return fact(state, id, new ShipmentColdTransferred(id, shipment.revision(), shipment.status()));
    }
    private static FrontierWorldState fact(FrontierWorldState state, SubjectId owner, FrontierPayload event) {
        return GoodsTradeTest.factForFamily(state, owner, event, 0, "shipments");
    }
    private static FrontierWorldState restored(FrontierWorldState state) {
        var codec = new FrontierWorldStateCodec(); var result = codec.decode(codec.encode(state)); assertEquals(state, result); return result;
    }
}
