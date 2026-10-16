package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceStorageAdoptionTest {
    @Test void marketPublishesAlternativeWarehousesWithoutMultiplyingTheParticipantsDemand() {
        var initial = initial(); var home = initial.bootstrap().settlements().getFirst().id();
        var adoption = event(initial, true); var id = adoption.declaration().container().id();
        var state = adoption.apply(initial, home);
        state = state.withInventory(state.inventory().withSurfaceStatus(id, ContainerSurfaceStatus.ACTIVE));
        var participant = state.companies().goodsTrade().participants().participants().get(home);
        var buy = new GoodsParticipantPolicy.Intent("minecraft:bread", GoodsTradeOrder.Side.BUY, 64, FixedScalar.whole(1));
        assertEquals(Set.of(id, FrontierWorldState.depotId(home)), GoodsParticipantStorage.quoteEndpoints(state, participant, buy)
                .stream().map(ShipmentEndpoint::containerId).collect(java.util.stream.Collectors.toSet()));
        var sell = new GoodsParticipantPolicy.Intent("minecraft:bread", GoodsTradeOrder.Side.SELL, 3, FixedScalar.whole(1));
        assertEquals(List.of(id), GoodsParticipantStorage.quoteEndpoints(state, participant, sell).stream().map(ShipmentEndpoint::containerId).toList());
        var events = io.farfrontier.palemirror.frontier.v3.process.GoodsParticipantProcess.plan(state,
                io.farfrontier.palemirror.frontier.v3.process.GoodsParticipantProcess.review(home, 1), new SimInstant(1));
        var orders = events.stream().map(ProposedEvent::payload).filter(GoodsTradeOrderPlaced.class::isInstance)
                .map(GoodsTradeOrderPlaced.class::cast).map(GoodsTradeOrderPlaced::order)
                .filter(order -> order.itemKind().equals("minecraft:bread") && order.side() == GoodsTradeOrder.Side.BUY).toList();
        assertEquals(Set.of(id, FrontierWorldState.depotId(home)), orders.stream().map(GoodsTradeOrder::containerId)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(1, orders.stream().map(GoodsTradeOrder::quantity).distinct().count(), "alternative points share one demand policy");
        assertTrue(events.stream().noneMatch(event -> event.payload() instanceof GoodsTradeReserved), "quotes are not independent reservations");
        var retired = state.withInventory(state.inventory().withSurfaceStatus(id, ContainerSurfaceStatus.RETIRED));
        assertTrue(GoodsParticipantStorage.quoteEndpoints(retired, participant, sell).isEmpty());
    }
    @Test void replacementWarehouseOwnsMarketStockHarvestCapacityAndServiceFootprintAfterRecovery() {
        var initial = initial();
        var home = initial.bootstrap().settlements().getFirst();
        var old = FrontierWorldState.depotId(home.id());
        var oldPoint = ServiceBoundaryComposition.declaration(initial, old);
        ServiceBoundaryComposition.geometries(initial); // Warm the same index used by live HOT clearance.
        var adoption = event(initial, true);
        var id = adoption.declaration().container().id();
        var state = adoption.apply(initial, home.id());
        state = state.withInventory(state.inventory().withSurfaceStatus(id, ContainerSurfaceStatus.ACTIVE)
                .withSurfaceStatus(old, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(old, ContainerSurfaceStatus.ACTIVE)
                .withSurfaceStatus(old, ContainerSurfaceStatus.RETIRED));
        var codec = new FrontierWorldStateCodec();
        for (var current : List.of(state, codec.decode(codec.encode(state)))) {
            var participant = current.companies().goodsTrade().participants().participants().get(home.id());
            var view = GoodsParticipantView.read(current, participant);
            assertEquals(3, view.stocks().get("minecraft:bread").ownedInStorage());
            assertEquals(17, view.stocks().get("minecraft:cobblestone").unclaimedInStorage());
            assertEquals(oldPoint, ServiceBoundaryComposition.declaration(current, old), "retained work can still resolve old identity");
            assertFalse(ServiceBoundaryComposition.operationalDeclarations(current).containsKey(old));
            var point = ServiceBoundaryComposition.declaration(current, id);
            var station = adoption.declaration().container().storage().orElseThrow().endpoint().station();
            assertEquals(point, ServiceBoundaryComposition.turnoverPoint(current, station).orElseThrow());
            assertTrue(ServiceBoundaryComposition.occupancyChanged(current, station.standingBody(), station.standingBody().offset(1, 0, 0)));
            var visitor = current.bootstrap().settlements().get(1).residents().getFirst().id();
            assertTrue(io.farfrontier.palemirror.frontier.v3.process.AmbientBodyConfirmationProcess.serviceOccupancyChanged(
                    current, visitor, station.standingBody(), station.standingBody().offset(1, 0, 0)), "visitor's home cannot choose the observed boundary");
            var located = current.withActorBody(visitor, station.standingBody());
            var lease = io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess.nextLease(located, visitor, new SimInstant(1));
            var prepared = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.prepare(located, lease);
            var candidates = AmbientPlacementPolicy.candidates(prepared, lease);
            assertEquals(station, candidates.getFirst(), "exact retained body always has placement precedence");
            var alternative = candidates.stream().filter(value -> !value.equals(station)).findFirst().orElseThrow();
            var proof = AmbientPlacementPolicy.selectedRoute(prepared, lease, alternative);
            assertTrue(proof.isPresent());
            AmbientPlacementPolicy.requireSelected(codec.decode(codec.encode(prepared)), lease, alternative, proof);
            assertThrows(IllegalArgumentException.class, () -> AmbientPlacementPolicy.requireSelected(prepared, lease, alternative, Optional.empty()));
            assertTrue(ResourceStorageSelection.newStackCandidates(current, home.id(), "minecraft:wheat", 64, station,
                    Set.of(ResourceStorageRole.WAREHOUSE)).stream().anyMatch(endpoint -> endpoint.containerId().equals(id)));
            assertFalse(GoodsParticipantStorage.eligible(current, participant, old, "minecraft:bread"));
            assertTrue(io.farfrontier.palemirror.frontier.v3.process.GoodsParticipantWakeup.container(current, id, "changed-stock", 1)
                    .stream().anyMatch(event -> event.subject().equals(home.id())));
            var unavailable = current.withInventory(current.inventory().withSurfaceStatus(id, ContainerSurfaceStatus.CONFLICT));
            assertEquals(0, GoodsParticipantView.read(unavailable, participant).stocks().get("minecraft:bread").unclaimedInStorage());
            var retired = current.withInventory(current.inventory().withSurfaceStatus(id, ContainerSurfaceStatus.RETIRED));
            assertFalse(ServiceBoundaryComposition.operationalDeclarations(retired).containsKey(id));
            assertFalse(ServiceBoundaryComposition.occupancyChanged(retired, station.standingBody(), station.standingBody().offset(1, 0, 0)));
        }
    }
    private FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:adoption"), 41L,
                FrontierRulesets.installed("frontier-v3-quarry-graybox-r6")));
    }
    private ResourceStorageAdopted event(FrontierWorldState state, boolean filled) {
        var home = state.bootstrap().settlements().getFirst(); var anchor = home.anchor();
        var station = KnownPedestrianGround.forFrontier(state).at(anchor.x() + 22, anchor.z() + 22);
        var id = new SubjectId("container:adoption-test");
        var endpoint = new ShipmentEndpoint.Storage(home.id(), id, station, Optional.empty(), station);
        var record = new ContainerRecord(id, home.id(), 27, ContainerPurpose.RESOURCE_STORAGE, Optional.empty(),
                Optional.of(new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 0, ResourceStorageRole.WAREHOUSE)));
        return new ResourceStorageAdopted(new ResourceStorageDeclared(record, station.support().offset(1, 1, 0)), filled
                ? List.of(new ResourceLot(new SubjectId("lot:adopted-stone"), home.id(), "minecraft:cobblestone", 17,
                    "observed-territorial-storage", List.of()), new ResourceLot(new SubjectId("lot:adopted-bread"), home.id(),
                    "minecraft:bread", 3, "observed-territorial-storage", List.of())) : List.of());
    }
    @Test void emptyAndFilledAreAdmittedOnceButUnavailableUntilPhysicalConfirmationAndRecoverExactly() {
        for (boolean filled : List.of(false, true)) {
            var state = initial(); var event = event(state, filled); var container = event.declaration().container();
            var adopted = event.apply(state, container.ownerId());
            assertEquals(ContainerSurfaceStatus.ADOPTING, adopted.inventory().surfaces().get(container.id()).status());
            assertTrue(ReferenceContainerCustody.blocksCanonicalUse(adopted, container.id()));
            assertFalse(ContainerStorageAdmission.receive(adopted, container.id(), "minecraft:bread", 1, Optional.empty()));
            assertEquals(filled ? 20 : 0, adopted.inventory().fungibleResources().accounts().values().stream()
                    .filter(account -> account.custody().equals(new ResourceCustody.Container(container.id())))
                    .flatMap(account -> account.lotQuantities().values().stream()).mapToInt(Integer::intValue).sum());
            assertEquals(state.inventory().fungibleResources().bindings(), adopted.inventory().fungibleResources().bindings());
            var codec = new FrontierWorldStateCodec(); assertEquals(adopted, codec.decode(codec.encode(adopted)));
            var payloads = FrontierWorldPayloadCodecs.create();
            assertEquals(event, payloads.decode(event.type(), payloads.encode(event)));
            assertThrows(IllegalArgumentException.class, () -> event.apply(adopted, container.ownerId()));
            assertThrows(IllegalArgumentException.class, () -> event.apply(state, new SubjectId("settlement:foreign")));
            var running = adopted.inventory().withSurfaceStatus(container.id(), ContainerSurfaceStatus.ACTIVE);
            var active = adopted.withInventory(running);
            assertFalse(ReferenceContainerCustody.blocksCanonicalUse(active, container.id()));
            if (filled) {
                assertEquals(3, SettlementFoodPolicy.usableBread(active, container.ownerId()));
                var accountId = ReferenceContainerCustody.scopeId(container.id());
                var resources = active.inventory().fungibleResources();
                var stacks = List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                        new InventoryCustody.ContainerSlot(container.id(), 0)), "minecraft:bread", 3),
                        new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                            new InventoryCustody.ContainerSlot(container.id(), 1)), "minecraft:cobblestone", 17));
                resources = resources.rebind(accountId, 1L, FungiblePhysicalObservation.bind(resources, accountId, 1L, stacks));
                var hot = active.withInventory(active.inventory().withFungibleResources(resources));
                assertEquals(0, SettlementFoodPolicy.coldUsableBread(hot, container.ownerId()));
                assertEquals(3, SettlementFoodPolicy.usableBread(hot, container.ownerId()),
                        "physical binding is not food absence; the HOT service retains its own admission fence");
                var retired = hot.withInventory(hot.inventory().withSurfaceStatus(container.id(), ContainerSurfaceStatus.RETIRED));
                assertEquals(0, SettlementFoodPolicy.usableBread(retired, container.ownerId()));
            }
        }
    }
    @Test void defaultTerritoryIsBoundedAndForeignTitleCannotBeReissued() {
        var state = initial(); var home = state.bootstrap().settlements().getFirst();
        assertEquals(home.id(), SettlementStorageTerritory.owner(state.bootstrap(), home.anchor()).orElseThrow().id());
        assertTrue(SettlementStorageTerritory.owner(state.bootstrap(), new BlockPosition(0, 64, 0)).isEmpty());
        var event = event(state, false);
        assertThrows(IllegalArgumentException.class, () -> new ResourceStorageAdopted(event.declaration(),
                List.of(new ResourceLot(new SubjectId("lot:foreign"), new SubjectId("settlement:foreign"), "minecraft:bread", 3,
                    "foreign-owned", List.of()))));
        var occupied = state.inventory().surfaces().values().stream().filter(ContainerSurface::fixed).findFirst().orElseThrow().position();
        var moved = new ResourceStorageAdopted(new ResourceStorageDeclared(event.declaration().container(), occupied), List.of());
        assertThrows(IllegalArgumentException.class, () -> moved.apply(state, home.id()));
    }
    @Test void declaredMineLandAdmitsItsOwnerAndRetiredStorageDoesNotOwnTheReplacementSocket() {
        var state = initial();
        var site = state.extractionSites().deposits().values().stream()
                .filter(deposit -> deposit.site().settlementId().equals(new SubjectId("settlement:6"))).findFirst().orElseThrow().site();
        var socket = state.inventory().surfaces().get(site.containerId()).position();
        assertEquals(site.settlementId(), SettlementStorageTerritory.owner(state, socket).orElseThrow().id());
        var original = event(state, false); var record = original.declaration().container();
        var adopted = original.apply(state, record.ownerId());
        var active = adopted.withInventory(adopted.inventory().withSurfaceStatus(record.id(), ContainerSurfaceStatus.ACTIVE));
        var retired = active.withInventory(active.inventory().withSurfaceStatus(record.id(), ContainerSurfaceStatus.RETIRED));
        assertTrue(ReferenceContainerCustody.blocksCanonicalUse(retired, record.id()));
        var replacementId = new SubjectId("container:new-incarnation");
        var oldPort = (ShipmentEndpoint.Storage) record.storage().orElseThrow().endpoint();
        var port = new ShipmentEndpoint.Storage(oldPort.settlementId(), replacementId, oldPort.station(), oldPort.facilityId(), oldPort.exteriorApproach());
        var replacement = new ContainerRecord(replacementId, record.ownerId(), 27, ContainerPurpose.RESOURCE_STORAGE,
                Optional.empty(), Optional.of(new ResourceStoragePolicy(port, ResourceStorageFilter.all(), 0, ResourceStorageRole.WAREHOUSE)));
        var declaration = new ResourceStorageDeclared(replacement, original.declaration().position());
        assertDoesNotThrow(() -> declaration.apply(retired, record.ownerId()));
        assertThrows(IllegalArgumentException.class, () -> declaration.apply(active, record.ownerId()));
    }
}
