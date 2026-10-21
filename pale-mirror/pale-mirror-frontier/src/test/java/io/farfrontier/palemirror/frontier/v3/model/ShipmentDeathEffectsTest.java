package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Modeled domain cut points through the real command planner, not native/crash evidence. */
class ShipmentDeathEffectsTest {
    @Test void pendingPickupDeathSettlesOnlyItsWitnessedOutcome() {
        var state = GoodsTradeTest.reserved(); var shipment = GoodsTradeTest.shipment(state);
        var actor = shipment.execution().actorId();
        state = GoodsTradeTest.factForFamily(state, shipment.sender().settlementId(), new ShipmentDispatched(shipment), 0, "shipments");
        var actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(actor, actors.get(actor).withBody(BodyPosition.above(shipment.sender().station())));
        state = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        state = ModeledActorBodyFacts.present(state, actor);
        var identity = new ActorActuationId(ActorBodyAuthority.current(state, actor), shipment.execution());
        var lease = new AmbientActorLease(actor, state.actorLocations().get(actor).body(), new SimInstant(0), 1,
                AmbientLeaseStatus.HOT, AmbientGoalKind.WORK, state.actorLocations().get(actor).body());
        state = state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(Map.of(actor, lease)));
        var container = shipment.sender().container().orElseThrow();
        state = ReferenceContainerCustodyFixtures.observedAndHeld(state, container);
        var source = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(container, 0));
        var resources = state.inventory().fungibleResources();
        resources = resources.rebind(shipment.sourceAccountId(), 1, FungiblePhysicalObservation.bind(resources,
                shipment.sourceAccountId(), 1, List.of(new FungiblePhysicalObservation.Stack(source, shipment.itemKind(), 64))));
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        var step = new ShipmentPhysicalStep(shipment.status(), shipment.revision(), new ActorHotObservation(identity, lease.revision()),
                MaterialSourceSelection.select(resources, shipment.itemOrder()), -1, identity.body().physicalEpoch(), shipment.lotQuantities(), 0);
        state = ShipmentConfirmationFixture.apply(state, new ShipmentHotPrepared(shipment.id(), step));
        var prepared = state;
        var pickup = new ShipmentHotTransferred(shipment.id(), step,
                List.of(new FungiblePhysicalObservation.Stack(source, shipment.itemKind(), 4)),
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(actor,
                        ActorBodyId.entityId(state.bootstrap().worldId(), actor), ActorContainerItemOrder.Hand.MAIN), shipment.itemKind(), 60)));
        checkUnloadingDeath(ShipmentConfirmationFixture.apply(prepared, pickup), shipment.id());
        var dead = ActorBodyAuthority.died(state, ModeledActorBodyFacts.death(state, actor, state.actorLocations().get(actor).body(),
                "fixture:shipment-pending-death"), FrontierActorDeathConsequences.INSTANCE, 10);
        for (boolean applied : List.of(false, true)) {
            var receipt = new ShipmentRetiredEffectObserved(new ShipmentHotTransferred(shipment.id(), step,
                    applied ? List.of(new FungiblePhysicalObservation.Stack(source, shipment.itemKind(), 4)) : List.of(),
                    applied ? List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(actor,
                            ActorBodyId.entityId(state.bootstrap().worldId(), actor), ActorContainerItemOrder.Hand.MAIN), shipment.itemKind(), 60))
                            : List.of()), applied);
            assertThrows(IllegalArgumentException.class, () -> ShipmentConfirmationFixture.apply(prepared, receipt));
            var next = ShipmentConfirmationFixture.apply(dead, receipt);
            var following = next.shipments().shipments().get(shipment.id());
            assertTrue(following.pendingPhysicalStep().isEmpty());
            assertEquals(applied ? Shipment.Status.CARRYING : Shipment.Status.AWAITING_LOAD, following.status());
            assertEquals(total(dead), total(next));
            assertEquals(dead.inventory().economics(), next.inventory().economics());
            assertThrows(IllegalArgumentException.class, () -> ShipmentConfirmationFixture.apply(next, receipt));
            var release = new ShipmentDeathSettled(shipment.id(), following.revision(), identity);
            if (applied) {
                assertThrows(IllegalArgumentException.class, () -> ShipmentConfirmationFixture.apply(next, release));
                var disposition = new ShipmentCargoDispositionObserved(shipment.id(), following.revision(), identity,
                        ShipmentCargoDispositionObserved.Outcome.WORLD_DROP, Optional.of(new ResourceCustody.Ground(
                                UUID.nameUUIDFromBytes("shipment-death-fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8)), new BlockPosition(0, 64, 0))));
                var dropped = ShipmentConfirmationFixture.apply(next, disposition);
                assertEquals(total(dead), total(dropped));
                assertEquals(Shipment.Status.CARGO_DISPOSED, dropped.shipments().shipments().get(shipment.id()).status());
                roundTrip(dropped);
            } else {
                var released = ShipmentConfirmationFixture.apply(next, release);
                assertEquals(Shipment.Status.ALLOCATION_WITHDRAWN, released.shipments().shipments().get(shipment.id()).status());
                assertEquals(total(dead), total(released));
                assertFalse(released.inventory().fungibleResources().claims().containsKey(shipment.authorization().claimId()));
                assertEquals(dead.inventory().economics().accounts(), released.inventory().economics().accounts());
                assertTrue(released.inventory().economics().reservations().isEmpty());
                assertThrows(IllegalArgumentException.class, () -> ShipmentConfirmationFixture.apply(released, release));
                roundTrip(released);
            }
            roundTrip(next);
        }
    }
    private static void checkUnloadingDeath(FrontierWorldState state, SubjectId shipmentId) {
        var shipment = state.shipments().shipments().get(shipmentId); var actor = shipment.execution().actorId();
        var actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(actor, actors.get(actor).withBody(BodyPosition.above(shipment.receiver().station())));
        state = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).ambientLeases(Map.of(actor,
                new AmbientActorLease(actor, actors.get(actor).body(), new SimInstant(0), 1,
                        AmbientLeaseStatus.HOT, AmbientGoalKind.WORK, actors.get(actor).body()))));
        var container = shipment.receiver().containerId();
        state = ReferenceContainerCustodyFixtures.observedAndHeld(state, container);
        var resources = state.inventory().fungibleResources();
        var totals = new TreeMap<String, Integer>();
        var account = resources.accounts().get(shipment.receivingAccountId());
        if (account != null) account.lotQuantities().forEach((lot, quantity) -> totals.merge(resources.lots().get(lot).itemKind(), quantity, Integer::sum));
        var layout = new ArrayList<FungiblePhysicalObservation.Stack>();
        for (var entry : totals.entrySet()) for (int count = entry.getValue(); count > 0; count -= Math.min(count, 64))
            layout.add(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(
                    container, layout.size())), entry.getKey(), Math.min(count, 64)));
        if (account != null) state = state.withInventory(state.inventory().withFungibleResources(resources.rebind(account.id(), 1,
                FungiblePhysicalObservation.bind(resources, account.id(), 1, layout))));
        var identity = new ActorActuationId(ActorBodyAuthority.current(state, actor), shipment.execution());
        var target = ShipmentPhysicalStateSupport.destination(state, shipment).orElseThrow();
        var step = new ShipmentPhysicalStep(shipment.status(), shipment.revision(), new ActorHotObservation(identity, 1),
                MaterialSourceSelection.select(state.inventory().fungibleResources(), shipment.itemOrder(target.lots())),
                target.slot(), 1, target.lots(), target.before());
        state = ShipmentConfirmationFixture.apply(state, new ShipmentHotPrepared(shipment.id(), step));
        var dead = ActorBodyAuthority.died(state, ModeledActorBodyFacts.death(state, actor, actors.get(actor).body(),
                "fixture:shipment-unload-death"), FrontierActorDeathConsequences.INSTANCE, 10);
        var address = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(container, target.slot()));
        layout.removeIf(stack -> stack.address().equals(address));
        layout.add(new FungiblePhysicalObservation.Stack(address, shipment.itemKind(), target.before() + shipment.quantity()));
        assertEquals(shipment.lotQuantities(), target.lots(), "fixture needs full endpoint reception");
        for (boolean applied : List.of(false, true)) {
            var receipt = new ShipmentRetiredEffectObserved(new ShipmentHotTransferred(shipmentId, step, List.of(), applied ? layout : List.of()), applied);
            var next = ShipmentConfirmationFixture.apply(dead, receipt);
            var following = next.shipments().shipments().get(shipmentId);
            assertEquals(applied ? Shipment.Status.DELIVERED : Shipment.Status.CARRYING, following.status());
            assertEquals(applied, following.reception().isPresent());
            assertEquals(total(dead), total(next));
            assertEquals(dead.inventory().economics(), next.inventory().economics(), "only recipient acceptance may award payment");
            assertThrows(IllegalArgumentException.class, () -> ShipmentConfirmationFixture.apply(next, receipt));
            roundTrip(next);
        }
    }
    static void checkPreparedMobileDeath(FrontierWorldState prepared, ShipmentHotTransferred receipt) {
        var identity = receipt.step().observation().actuation(); var actor = identity.body().actorId();
        var shipment = prepared.shipments().shipments().get(receipt.shipmentId());
        assertTrue(shipment.mobileContainerId().isPresent());
        var dead = ActorBodyAuthority.died(prepared, ModeledActorBodyFacts.death(prepared, actor, prepared.actorLocations().get(actor).body(),
                "fixture:attached-shipment-death"), FrontierActorDeathConsequences.INSTANCE, 10);
        for (boolean applied : List.of(false, true)) {
            var effect = applied ? receipt : new ShipmentHotTransferred(shipment.id(), receipt.step(), List.of(), List.of());
            var observation = new ShipmentRetiredEffectObserved(effect, applied);
            var next = ShipmentConfirmationFixture.apply(dead, observation);
            assertEquals(total(dead), total(next));
            assertEquals(dead.inventory().economics(), next.inventory().economics());
            assertTrue(ReferenceContainerCustody.hasLiveCustody(next, shipment.mobileContainerId().orElseThrow()),
                    "dead attachment must retain exact native custody for its subsequent resource drop");
            assertTrue(next.inventory().fungibleResources().bindings().values().stream().anyMatch(binding ->
                            binding.address() instanceof PhysicalStackAddress.ContainerSlot slot
                                    && slot.slot().containerId().equals(shipment.mobileContainerId().orElseThrow())),
                    "closing transfer must not discard the attachment owner's drop preimage");
            assertThrows(IllegalArgumentException.class, () -> ShipmentConfirmationFixture.apply(next, observation));
            roundTrip(next);
        }
    }
    private static long total(FrontierWorldState state) {
        return state.inventory().fungibleResources().accounts().values().stream()
                .flatMap(a -> a.lotQuantities().values().stream()).mapToLong(Integer::longValue).sum();
    }
    private static void roundTrip(FrontierWorldState state) {
        var codec = new FrontierWorldStateCodec(); assertEquals(state, codec.decode(codec.encode(state)));
    }
}
