package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InternalShipmentTest {
    private RetargetFixture machineSupplyFixture() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:machine-supply"),
                20260918065L, FrontierRulesets.fuelledQuarryGraybox()));
        var home = state.bootstrap().settlements().getFirst();
        var station = ProductionStationSpec.grayboxBakery(home.structures().stream()
                .filter(value -> value.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow());
        var source = GoodsParticipantDeclarations.endpoint(home);
        var account = FungibleResourceCustodySupport.accountAtContainer(state, source.containerId()).orElseThrow();
        var lots = FungibleResourceCustodySupport.selectAtAccount(state.inventory().fungibleResources(),
                account.id(), home.id(), "minecraft:coal", 3).orElseThrow().lotQuantities();
        var facility = home.structures().stream().filter(value -> value.id().equals(station.facilityId())).findFirst().orElseThrow();
        var receiver = new ShipmentEndpoint.ProductionPort(home.id(), station, ActorContainerItemOrder.StationPort.FUEL,
                SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach());
        var worker = SettlementWorkforce.candidates(state, home.id(), ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS).getFirst();
        var id = new SubjectId("shipment:test-machine-supply"); var claim = new SubjectId("claim:test-machine-supply");
        var shipment = new Shipment(id, new ResourceClaimDelegation(ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT,
                claim, home.id(), id, 1), state.actorExecutions().next(worker.id(), ActorActivityKind.COURIER, id),
                source, receiver, account.id(), new SubjectId("custody:test-machine-supply-carrier"),
                ReferenceContainerCustody.scopeId(station.containerId()), "minecraft:coal", lots, Shipment.Status.AWAITING_LOAD, 1);
        state = InternalShipmentStateSupport.dispatch(state, home.id(), new InternalShipmentDispatched(shipment,
                new ClaimAllocation(claim, home.id(), home.id(), shipment.itemKind(), 3, lots, ClaimPurpose.INTERNAL_LOGISTICS)));
        return new RetargetFixture(state, shipment);
    }
    @Test void machineSupplyKeepsItsExactPortAndExecutesInsteadOfSeekingAnotherWarehouse() {
        var fixture = machineSupplyFixture(); var state = fixture.state(); var shipment = fixture.shipment();
        var id = shipment.id(); var actor = shipment.execution().actorId(); var source = shipment.sender();
        var receiver = (ShipmentEndpoint.ProductionPort) shipment.receiver(); var station = receiver.spec();
        var codec = new FrontierWorldStateCodec();
        state = codec.decode(codec.encode(state));
        assertTrue(ShipmentStorageRetargeted.retainedAvailable(state, shipment), "a surviving machine remains the exact supply destination");
        assertTrue(ShipmentStorageRetargeted.eligible(state, shipment).isEmpty());
        assertTrue(io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.plan(state,
                io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.progress(id, 1), 1).stream()
                .anyMatch(event -> event.payload() instanceof ActorMovementStarted), "fuel courier must begin its ordinary pickup journey");
        state = state.withActorBody(actor, source.station().standingBody());
        state = ShipmentStateSupport.transferCold(state, id, id, 1, Shipment.Status.AWAITING_LOAD);
        state = codec.decode(codec.encode(state));
        assertTrue(ShipmentStorageRetargeted.retainedAvailable(state, state.shipments().shipments().get(id)));
        state = state.withActorBody(actor, receiver.station().standingBody());
        state = ShipmentStateSupport.transferCold(state, id, id, 2, Shipment.Status.CARRYING);
        var delivered = state.shipments().shipments().get(id);
        state = InternalShipmentStateSupport.receive(state, id,
                new InternalShipmentReceived(id, delivered.revision(), delivered.reception().orElseThrow().id()));
        assertEquals(3, state.inventory().fungibleResources().unclaimedQuantity(
                ReferenceContainerCustody.scopeId(station.containerId()), receiver.settlementId(), shipment.itemKind()));
        assertEquals(state, codec.decode(codec.encode(state)));
    }

    @Test void machineReplacementRetargetsUnpickedOrCarriedSupplyOnlyToTheSameFacilityPortAndReplaysWithoutSearching() throws Exception {
        for (boolean carried : List.of(false, true)) {
            var fixture = machineSupplyFixture(); var state = fixture.state(); var shipment = fixture.shipment();
            var old = (ShipmentEndpoint.ProductionPort) shipment.receiver(); var actor = shipment.execution().actorId();
            state = state.withActorBody(actor, shipment.sender().station().standingBody());
            if (carried) {
                state = ShipmentStateSupport.transferCold(state, shipment.id(), shipment.id(), 1, Shipment.Status.AWAITING_LOAD);
                shipment = state.shipments().shipments().get(shipment.id());
            }
            var cargoBefore = state.inventory().fungibleResources();
            var replacementPolicy = ShipmentEndpointComposition.replacementPolicy(old);
            assertFalse(replacementPolicy.admits(state, shipment, GoodsParticipantDeclarations.endpoint(
                    FrontierWorldStateSupport.settlement(state.bootstrap(), old.settlementId()))));
            var position = state.inventory().surfaces().get(old.containerId()).position();
            state = state.withInventory(state.inventory().withSurfaceStatus(old.containerId(), ContainerSurfaceStatus.PREPARED)
                    .withSurfaceStatus(old.containerId(), ContainerSurfaceStatus.ACTIVE).withSurfaceStatus(old.containerId(), ContainerSurfaceStatus.RETIRED));
            state = new ProductionStationRetired(old.spec().id(), 1).apply(state, old.spec().id());
            assertFalse(ShipmentStorageRetargeted.retainedAvailable(state, shipment));
            assertTrue(ShipmentStorageRetargeted.eligible(state, shipment).isEmpty(), "absence waits without discarding in-flight fuel");
            var spec = new ProductionStationSpec(new SubjectId("station:resupplied-machine"), old.spec().facilityId(),
                    new SubjectId("container:resupplied-machine"), old.spec().equipmentType(), old.spec().capability(),
                    old.station(), old.spec().socketSurface(), 0, 1, 2);
            var record = new ContainerRecord(spec.containerId(), old.settlementId(), 3, ContainerPurpose.PRODUCTION_STATION,
                    Optional.of(spec), Optional.empty());
            state = new ProductionStationInstalled(record, position, List.of()).apply(state, spec.id(), BakeryStationInstallation::installed);
            state = state.withInventory(state.inventory().withSurfaceStatus(spec.containerId(), ContainerSurfaceStatus.ACTIVE));
            var event = ShipmentStorageRetargeted.eligible(state, shipment).orElseThrow();
            var payloads = FrontierWorldPayloadCodecs.create();
            var decoded = (ShipmentStorageRetargeted) payloads.decode(event.type(), payloads.encode(event));
            var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
            try (var ignored = PedestrianRoutePlanning.bind((geometry, origin, target) -> {
                fail("receiver replacement replay must not search"); return null;
            })) { state = decoded.apply(state, shipment.id()); }
            shipment = state.shipments().shipments().get(shipment.id());
            var next = (ShipmentEndpoint.ProductionPort) shipment.receiver();
            assertEquals(spec, next.spec()); assertEquals(old.port(), next.port());
            assertEquals(cargoBefore, state.inventory().fungibleResources());
            if (!carried) {
                state = ShipmentStateSupport.transferCold(state, shipment.id(), shipment.id(), shipment.revision(), Shipment.Status.AWAITING_LOAD);
                shipment = state.shipments().shipments().get(shipment.id());
            }
            state = state.withActorBody(actor, next.station().standingBody());
            state = ShipmentStateSupport.transferCold(state, shipment.id(), shipment.id(), shipment.revision(), Shipment.Status.CARRYING);
            shipment = state.shipments().shipments().get(shipment.id());
            state = InternalShipmentStateSupport.receive(state, shipment.id(), new InternalShipmentReceived(shipment.id(),
                    shipment.revision(), shipment.reception().orElseThrow().id()));
            assertEquals(3, state.inventory().fungibleResources().unclaimedQuantity(
                    shipment.receivingAccountId(), old.settlementId(), shipment.itemKind()));
            assertEquals(state, codec.decode(codec.encode(state)));
        }
    }

    private record RetargetFixture(FrontierWorldState state, Shipment shipment) { }
    @Test void retargetDuringAnActiveJourneyOwnsExactlyOneShipmentContinuation() {
        var fixture = retargetFixture(); var state = fixture.state(); var shipment = fixture.shipment();
        var origin = KnownPedestrianGround.forFrontier(state).at(
                FrontierWorldStateSupport.settlement(state.bootstrap(), shipment.receiver().settlementId()).anchor().x() + 20,
                FrontierWorldStateSupport.settlement(state.bootstrap(), shipment.receiver().settlementId()).anchor().z() + 20);
        state = state.withActorBody(shipment.execution().actorId(), origin.standingBody());
        var movement = new ActorMovement(shipment.movementOrder(), 10,
                new ActorMovementContext.ShipmentLeg(shipment.id(), shipment.revision()), shipment.execution());
        state = io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.reduceStarted(state,
                shipment.execution().actorId(), new ActorMovementStarted(movement));
        var codec = new FrontierWorldStateCodec(); state = codec.decode(codec.encode(state));
        var action = io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.progress(shipment.id(), 20);
        var events = io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.plan(state, action, 20);
        assertEquals(1, events.stream().filter(e -> e.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled r
                && r.scheduleId().equals(action.id())).count());
        assertTrue(events.stream().anyMatch(e -> e.payload() instanceof ActorMovementInterrupted));
        assertTrue(events.stream().anyMatch(e -> e.payload() instanceof ShipmentStorageRetargeted));
    }
    private RetargetFixture retargetFixture() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:haul-retarget"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r6")));
        var site = state.extractionSites().deposits().values().iterator().next().site();
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), site.settlementId());
        var source = new SubjectId("custody:test-retarget-source"); var lot = new SubjectId("lot:test-retarget");
        var ledger = state.inventory().fungibleResources().issue(
                new ResourceLot(lot, home.id(), "minecraft:cobblestone", 10, "retarget-source", List.of()),
                new CustodyAccount(source, new ResourceCustody.Container(QuarryStorageFixture.container(state, site)), Map.of(lot, 10), Map.of()));
        state = state.withInventory(state.inventory().withFungibleResources(ledger));
        var worker = SettlementWorkforce.candidates(state, home.id(), ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS).getFirst();
        var receiver = GoodsParticipantDeclarations.endpoint(home);
        var receiving = FungibleResourceCustodySupport.accountAtContainer(state, receiver.containerId()).map(CustodyAccount::id)
                .orElseGet(() -> ReferenceContainerCustody.scopeId(receiver.containerId()));
        var id = new SubjectId("shipment:test-retarget"); var claim = new SubjectId("claim:test-retarget");
        var shipment = new Shipment(id, new ResourceClaimDelegation(ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT,
                claim, home.id(), id, 1), state.actorExecutions().next(worker.id(), ActorActivityKind.COURIER, id),
                new ShipmentEndpoint.ExtractiveSite(home.id(), site.id(), QuarryStorageFixture.container(state, site), site.layout().storagePort()), receiver,
                source, new SubjectId("custody:test-retarget-carrier"), receiving, "minecraft:cobblestone", Map.of(lot, 10),
                Shipment.Status.AWAITING_LOAD, 1);
        state = InternalShipmentStateSupport.dispatch(state, home.id(), new InternalShipmentDispatched(shipment,
                new ClaimAllocation(claim, home.id(), home.id(), shipment.itemKind(), 10, Map.of(lot, 10), ClaimPurpose.INTERNAL_LOGISTICS)));
        // Exact ordinary boundary: old receiver is unavailable, observed new warehouse is active.
        state = state.withActorBody(worker.id(), receiver.station().standingBody());
        state = state.withInventory(state.inventory().withSurfaceStatus(receiver.containerId(), ContainerSurfaceStatus.CONFLICT));
        var station = KnownPedestrianGround.forFrontier(state).at(home.anchor().x() + 22, home.anchor().z() + 22);
        var container = new SubjectId("container:test-retarget");
        var endpoint = new ShipmentEndpoint.Storage(home.id(), container, station, Optional.empty(), station);
        var record = new ContainerRecord(container, home.id(), 27, ContainerPurpose.RESOURCE_STORAGE, Optional.empty(),
                Optional.of(new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 0, ResourceStorageRole.WAREHOUSE)));
        state = new ResourceStorageAdopted(new ResourceStorageDeclared(record, station.support().offset(1, 1, 0)), List.of()).apply(state, home.id());
        state = state.withInventory(state.inventory().withSurfaceStatus(container, ContainerSurfaceStatus.ACTIVE));
        return new RetargetFixture(state, shipment);
    }
    @Test void lostReceivingStorageReplaysSelectedRouteWithoutSearchingAndKeepsCargoAndTitle() throws Exception {
        var fixture = retargetFixture(); var state = fixture.state(); var shipment = fixture.shipment();
        var event = ShipmentStorageRetargeted.eligible(state, shipment).orElseThrow();
        var payloads = FrontierWorldPayloadCodecs.create();
        var decoded = (ShipmentStorageRetargeted) payloads.decode(event.type(), payloads.encode(event));
        assertEquals(event, decoded);
        var codec = new FrontierWorldStateCodec(); var recovered = codec.decode(codec.encode(state));
        try (var ignored = PedestrianRoutePlanning.bind((geometry, origin, target) -> {
            fail("retained warehouse selection may not invoke a planner during replay"); return null;
        })) {
            var after = decoded.apply(recovered, shipment.id());
            assertEquals(event.destination(), after.shipments().shipments().get(shipment.id()).receiver());
            assertEquals(state.inventory(), after.inventory()); assertEquals(state.actorLocations(), after.actorLocations());
            assertEquals(state.actorExecutions(), after.actorExecutions());
            assertEquals(after, codec.decode(codec.encode(after)));
            assertThrows(IllegalArgumentException.class, () -> decoded.apply(after, shipment.id()));
            assertThrows(IllegalArgumentException.class, () -> decoded.apply(recovered, new SubjectId("shipment:foreign")));
            var moved = recovered.withActorBody(shipment.execution().actorId(), event.destination().station().standingBody());
            assertThrows(IllegalArgumentException.class, () -> decoded.apply(moved, shipment.id()));
            var lost = recovered.withInventory(recovered.inventory().withSurfaceStatus(event.destination().containerId(), ContainerSurfaceStatus.CONFLICT));
            assertThrows(IllegalArgumentException.class, () -> decoded.apply(lost, shipment.id()));
            assertThrows(IllegalArgumentException.class, () -> new ShipmentStorageRetargeted(shipment.id(), shipment.revision(),
                    shipment.execution(), event.destination().station(), event.destination(), event.route()));
        }
    }
    @Test void unresolvedRetargetPlanningIsNotARejectedWarehouseOrACommittedReplacement() throws Exception {
        var fixture = retargetFixture(); var state = fixture.state(); var shipment = fixture.shipment();
        for (var status : List.of(PedestrianRouteResult.Status.PLANNING, PedestrianRouteResult.Status.UNKNOWN_GEOMETRY,
                PedestrianRouteResult.Status.SAFETY_LIMIT)) {
            try (var ignored = PedestrianRoutePlanning.bind((geometry, origin, target) ->
                    new PedestrianRouteResult(status, List.of(), 0, 0, "retarget planning wait"))) {
                var unavailable = assertThrows(KnownPedestrianNavigation.RouteUnavailable.class,
                        () -> ShipmentStorageRetargeted.eligible(state, shipment));
                assertEquals(status, unavailable.status());
                var observation = PedestrianRoutePlanning.observe(() -> io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.plan(
                        state, io.farfrontier.palemirror.frontier.v3.process.ShipmentProcess.progress(shipment.id(), 1200), 1200));
                assertTrue(observation.result().stream().allMatch(e -> e.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled));
                if (status == PedestrianRouteResult.Status.PLANNING) assertFalse(observation.requests().isEmpty());
                assertEquals(shipment, state.shipments().shipments().get(shipment.id()));
            }
        }
    }
    @Test void witnessedQuarryWithdrawalAtomicallyRetiresUnpickedHaulAndSurvivesRecovery() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:quarry-player-loss"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r4")));
        var site = state.extractionSites().deposits().values().iterator().next().site();
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), site.settlementId());
        var source = new SubjectId("custody:test-quarry-player-loss");
        var lot = new SubjectId("lot:test-quarry-player-loss");
        var claimId = new SubjectId("claim:test-quarry-player-loss");
        var shipmentId = new SubjectId("shipment:test-quarry-player-loss");
        var ledger = state.inventory().fungibleResources().issue(
                new ResourceLot(lot, home.id(), "minecraft:cobblestone", 36, "isolated-loss-fixture", List.of()),
                new CustodyAccount(source, new ResourceCustody.Container(QuarryStorageFixture.container(state, site)), Map.of(lot, 36), Map.of()));
        state = state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(ledger)));
        var worker = SettlementWorkforce.candidates(state, home.id(), ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS).getFirst();
        var receiver = GoodsParticipantDeclarations.endpoint(home);
        var receiving = FungibleResourceCustodySupport.accountAtContainer(state, receiver.containerId()).map(CustodyAccount::id)
                .orElseGet(() -> ReferenceContainerCustody.scopeId(receiver.containerId()));
        var shipment = new Shipment(shipmentId, new ResourceClaimDelegation(ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT,
                claimId, home.id(), shipmentId, 1), state.actorExecutions().next(worker.id(), ActorActivityKind.COURIER, shipmentId),
                new ShipmentEndpoint.ExtractiveSite(home.id(), site.id(), QuarryStorageFixture.container(state, site), site.layout().storagePort()), receiver,
                source, new SubjectId("custody:test-quarry-player-carrier"), receiving, "minecraft:cobblestone", Map.of(lot, 36),
                Shipment.Status.AWAITING_LOAD, 1);
        state = InternalShipmentStateSupport.dispatch(state, home.id(), new InternalShipmentDispatched(shipment,
                new ClaimAllocation(claimId, home.id(), home.id(), shipment.itemKind(), 36, Map.of(lot, 36), ClaimPurpose.INTERNAL_LOGISTICS)));
        var inventoryBeforeBinding = state.inventory();
        var sourceContainer = QuarryStorageFixture.container(state, site);
        int freeSlot = java.util.stream.IntStream.range(0, 27)
                .filter(slot -> inventoryBeforeBinding.itemAt(sourceContainer, slot).isEmpty()).findFirst().orElseThrow();
        var stack = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(QuarryStorageFixture.container(state, site), freeSlot)), "minecraft:cobblestone", 36);
        ledger = state.inventory().fungibleResources();
        state = state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(
                ledger.rebind(source, 1L, FungiblePhysicalObservation.bind(ledger, source, 1L, List.of(stack))))));
        assertEquals(Set.of(claimId), SourceStockClaimLoss.admissibleClaims(state, source));
        var loss = assertInstanceOf(FungibleStockDepartureObserved.class, FungibleContainerPlayerEdit.classify(
                state.inventory().fungibleResources(), source, QuarryStorageFixture.container(state, site), home.id(), 1L,
                UUID.randomUUID(), UUID.randomUUID(), List.of(), SourceStockClaimLoss.admissibleClaims(state, source)));
        var economics = state.inventory().economics();
        var after = SourceStockClaimLoss.settle(state, loss);
        var woken = ServiceAccessCoordinator.wakePoints(state, after);
        assertTrue(woken.contains(QuarryStorageFixture.container(state, site)), "retiring a courier wakes its exact source, not only the home depot");
        assertTrue(woken.contains(receiver.containerId()), "both sides of the retired transport demand are addressed");
        assertEquals(Shipment.Status.ALLOCATION_WITHDRAWN, after.shipments().shipments().get(shipmentId).status());
        assertFalse(after.inventory().fungibleResources().claims().containsKey(claimId));
        assertFalse(after.actorExecutions().current(ActorActivityKind.COURIER).containsKey(worker.id()));
        assertEquals(0, after.inventory().fungibleResources().totalQuantity(home.id(), "minecraft:cobblestone"));
        assertEquals(economics, after.inventory().economics());
        assertEquals(after, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(after)));
        assertThrows(IllegalArgumentException.class, () -> SourceStockClaimLoss.settle(after, loss));
    }

    @Test void sameOwnerHaulAcceptsExactStockWithoutSellingItAndRejectsForgedOrRepeatedReceipts() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:internal-receipt"),
                20260918065L, FrontierRulesets.installed("frontier-v3-quarry-graybox-r1")));
        var site = state.extractionSites().deposits().values().iterator().next().site();
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), site.settlementId());
        var source = new SubjectId("custody:test-quarry-stock"); var lot = new SubjectId("lot:test-quarry-stock");
        var claimId = new SubjectId("claim:test-local-haul"); var shipmentId = new SubjectId("shipment:test-local-haul");
        var resource = new ResourceLot(lot, home.id(), "minecraft:cobblestone", 10, "isolated-fixture", List.of());
        var ledger = state.inventory().fungibleResources().issue(resource,
                new CustodyAccount(source, new ResourceCustody.Container(QuarryStorageFixture.container(state, site)), Map.of(lot, 10), Map.of()));
        state = state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(ledger)));
        var worker = SettlementWorkforce.candidates(state, home.id(), ResidentWorkKind.LOGISTICS, HumanCapability.LOGISTICS).getFirst();
        var receiver = GoodsParticipantDeclarations.endpoint(home);
        var receiving = FungibleResourceCustodySupport.accountAtContainer(state, receiver.containerId()).map(CustodyAccount::id)
                .orElseGet(() -> ReferenceContainerCustody.scopeId(receiver.containerId()));
        var shipment = new Shipment(shipmentId, new ResourceClaimDelegation(ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT,
                claimId, home.id(), shipmentId, 1), state.actorExecutions().next(worker.id(), ActorActivityKind.COURIER, shipmentId),
                new ShipmentEndpoint.ExtractiveSite(home.id(), site.id(), QuarryStorageFixture.container(state, site), site.layout().storagePort()), receiver,
                source, new SubjectId("custody:test-local-hauler"), receiving, "minecraft:cobblestone", Map.of(lot, 10), Shipment.Status.AWAITING_LOAD, 1);
        var claim = new ClaimAllocation(claimId, home.id(), home.id(), shipment.itemKind(), 10, shipment.lotQuantities(), ClaimPurpose.INTERNAL_LOGISTICS);
        var before = state;
        assertThrows(IllegalArgumentException.class, () -> InternalShipmentStateSupport.dispatch(before,
                new SubjectId("settlement:wrong-issuer"), new InternalShipmentDispatched(shipment, claim)));
        var economics = state.inventory().economics();
        var sources = state.extractionSites();
        state = InternalShipmentStateSupport.dispatch(state, home.id(), new InternalShipmentDispatched(shipment, claim));
        state = state.withActorBody(worker.id(), site.layout().storagePort().standingBody());
        state = ShipmentStateSupport.transferCold(state, shipmentId, shipmentId, 1, Shipment.Status.AWAITING_LOAD);
        state = state.withActorBody(worker.id(), receiver.station().standingBody());
        state = ShipmentStateSupport.transferCold(state, shipmentId, shipmentId, 2, Shipment.Status.CARRYING);
        var unloaded = state.shipments().shipments().get(shipmentId);
        assertEquals(Shipment.Status.DELIVERED, unloaded.status());
        assertTrue(state.inventory().fungibleResources().claims().containsKey(claimId));
        var expected = state;
        assertThrows(IllegalArgumentException.class, () -> InternalShipmentStateSupport.receive(expected, shipmentId,
                new InternalShipmentReceived(shipmentId, unloaded.revision(), new SubjectId("receipt:forged"))));
        var receipt = new InternalShipmentReceived(shipmentId, unloaded.revision(), unloaded.reception().orElseThrow().id());
        state = InternalShipmentStateSupport.receive(state, shipmentId, receipt);
        assertEquals(10, state.inventory().fungibleResources().unclaimedQuantity(receiving, home.id(), shipment.itemKind()));
        assertEquals(economics, state.inventory().economics());
        assertEquals(sources, state.extractionSites());
        assertFalse(state.inventory().fungibleResources().claims().containsKey(claimId));
        assertTrue(state.shipments().shipments().get(shipmentId).reception().isEmpty());
        var accepted = state;
        assertThrows(IllegalArgumentException.class, () -> InternalShipmentStateSupport.receive(accepted, shipmentId, receipt));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }
}
