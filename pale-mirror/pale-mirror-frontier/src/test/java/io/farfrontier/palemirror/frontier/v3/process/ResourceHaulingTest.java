package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Product path: an owned drop admits the existing courier/claim and reserves receiving space once. */
class ResourceHaulingTest {
    @Test void groundStockUsesOrdinaryInternalShipmentAndRetainsItsReservationAcrossRecovery() throws Exception {
        var initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:ground-haul"), 20260918065L,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r6")));
        var home = initial.bootstrap().settlements().getFirst(); var depot = initial.inventory().containers().get(FrontierWorldState.depotId(home.id()));
        var station = depot.storage().orElseThrow().endpoint().station(); var entity = UUID.randomUUID();
        var lot = new ResourceLot(new SubjectId("lot:ground-haul"), home.id(), "minecraft:cobblestone", 8, "test-causal-drop", List.of());
        var account = new CustodyAccount(new SubjectId("custody:ground-haul"), new ResourceCustody.Ground(entity, station.support().offset(0, 1, 0)),
                Map.of(lot.id(), 8), Map.of());
        var resources = initial.inventory().fungibleResources().issue(lot, account);
        resources = resources.rebind(account.id(), 1, FungiblePhysicalObservation.bind(resources, account.id(), 1,
                List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.WorldEntity(entity), lot.itemKind(), 8))));
        var state = initial.withInventory(initial.inventory().withFungibleResources(resources));
        var pickup = GroundResourcePlayerWithdrawal.prepare(state, account.id(), UUID.randomUUID(), UUID.randomUUID(), 3);
        var pickupCodecs = io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs.create();
        assertEquals(pickup, pickupCodecs.decode(pickup.type(), pickupCodecs.encode(pickup)));
        var partiallyPicked = pickup.apply(state, home.id());
        assertEquals(Map.of(lot.id(), 5), partiallyPicked.inventory().fungibleResources().accounts().get(account.id()).lotQuantities());
        assertEquals(5, partiallyPicked.inventory().fungibleResources().bindings().values().stream()
                .filter(binding -> binding.accountId().equals(account.id())).findFirst().orElseThrow().quantity());
        assertThrows(IllegalArgumentException.class, () -> pickup.apply(partiallyPicked, home.id()), "exact withdrawal cannot be charged twice");
        assertThrows(IllegalArgumentException.class, () -> pickup.apply(state, new SubjectId("settlement:foreign")));
        assertEquals(state.inventory().economics(), partiallyPicked.inventory().economics());
        var offer = state.humanPopulation().residents().values().stream().filter(worker -> worker.settlementId().equals(home.id()))
                .map(worker -> ResourceHaulingPolicy.offer(state, worker, 1200)).flatMap(Optional::stream).findFirst().orElseThrow();
        assertEquals(ResourceSourceEndpoint.Kind.GROUND, offer.shipment().sender().sourceKind());
        var dispatched = InternalShipmentStateSupport.dispatch(state, home.id(), offer);
        assertEquals(dispatched, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(dispatched)));
        assertEquals(8, dispatched.inventory().fungibleResources().claims().get(offer.claim().id()).quantity());
        assertEquals(home.id(), dispatched.inventory().fungibleResources().lots().get(lot.id()).economicOwnerId());
        assertEquals(new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.MAIN), offer.shipment().itemOrder().actorSlot());
        assertFalse(ResourceSourceComposition.coldAvailable(dispatched, offer.shipment().sender()));
        var source = (ResourceSourceEndpoint.Ground) offer.shipment().sender();
        var worker = state.humanPopulation().residents().get(offer.shipment().execution().actorId());
        for (var status : List.of(io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING,
                io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.UNKNOWN_GEOMETRY,
                io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.SAFETY_LIMIT)) {
            try (var ignored = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind((geometry, origin, target) ->
                    new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult(status, List.of(), 0, 0, "pickup wait"))) {
                var wait = assertThrows(io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable.class,
                        () -> InternalShipmentPlanning.prepare(state, worker, source, depot.storage().orElseThrow().endpoint(), lot.itemKind(), 8, 1201));
                assertEquals(status, wait.status());
                assertFalse(ResourceHaulingPolicy.available(state, worker, 1201));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> InternalShipmentPlanning.prepare(state,
                state.humanPopulation().residents().get(offer.shipment().execution().actorId()),
                new ResourceSourceEndpoint.Ground(source.settlementId(), source.custody(), source.station(), source.epoch() + 1),
                depot.storage().orElseThrow().endpoint(), lot.itemKind(), 8, 1201), "stale source epochs must fail before a new claim/mission");
        assertEquals(8L, ContainerInboundCapacity.incoming(List.of(new ContainerInboundCapacity.Demand(offer.shipment().id(),
                depot.id(), lot.itemKind(), 8)), depot.id(), Optional.empty()).get(lot.itemKind()));
        assertTrue(ContainerInboundCapacity.incoming(List.of(new ContainerInboundCapacity.Demand(offer.shipment().id(),
                depot.id(), lot.itemKind(), 8)), depot.id(), Optional.of(offer.shipment().id())).isEmpty());
        var loss = new GroundResourceDestroyed(account.id(), (ResourceCustody.Ground) account.custody(), 1, home.id(),
                UUID.randomUUID(), "DISCARDED", Map.of(lot.id(), 8), Set.of(offer.claim().id()));
        var codecs = io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs.create();
        var reservedPickup = GroundResourcePlayerWithdrawal.prepare(dispatched, account.id(), UUID.randomUUID(), UUID.randomUUID(), 4);
        assertEquals(Set.of(offer.claim().id()), reservedPickup.forfeitedClaims());
        var picked = reservedPickup.apply(dispatched, home.id());
        assertEquals(Shipment.Status.ALLOCATION_WITHDRAWN, picked.shipments().shipments().get(offer.shipment().id()).status());
        assertEquals(Map.of(lot.id(), 4), picked.inventory().fungibleResources().accounts().get(account.id()).lotQuantities());
        assertFalse(picked.inventory().fungibleResources().claims().containsKey(offer.claim().id()));
        assertEquals(picked, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(picked)));
        assertEquals(loss, codecs.decode(loss.type(), codecs.encode(loss)));
        var lost = loss.apply(dispatched, home.id());
        assertEquals(Shipment.Status.ALLOCATION_WITHDRAWN, lost.shipments().shipments().get(offer.shipment().id()).status());
        assertFalse(lost.inventory().fungibleResources().lots().containsKey(lot.id()));
        assertFalse(lost.inventory().fungibleResources().claims().containsKey(offer.claim().id()));
        assertEquals(dispatched.inventory().economics(), lost.inventory().economics());
        assertEquals(lost, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(lost)));
        assertThrows(IllegalArgumentException.class, () -> loss.apply(lost, home.id()), "a disappearance cannot be charged twice");
        assertThrows(IllegalArgumentException.class, () -> new GroundResourceDestroyed(account.id(), (ResourceCustody.Ground) account.custody(),
                1, home.id(), UUID.randomUUID(), "UNLOADED_TO_CHUNK", Map.of(lot.id(), 8), Set.of(offer.claim().id())));
    }
}
