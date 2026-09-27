package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionStationSpecTest {
    @Test
    void everyGrayboxBakeryDeclaresOnePersistedOwnedMachineSocketSeparateFromWorker() {
        FrontierWorldState state = FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:bakery-stations"), 91L));
        var stations = state.inventory().containers().values().stream()
                .flatMap(container -> container.productionStation().stream()).toList();
        assertEquals(state.bootstrap().settlements().size(), stations.size());
        for (ProductionStationSpec station : stations) {
            ContainerRecord container = state.inventory().containers().get(station.containerId());
            ContainerSurface surface = state.inventory().surfaces().get(station.containerId());
            assertEquals(ProductionStationSpec.Capability.BAKING, station.capability());
            assertEquals(new BlockPosition(station.socketSurface().x(), station.socketSurface().y() + 1,
                    station.socketSurface().z()), surface.position());
            assertFalse(station.workerStation().equals(station.socketSurface()));
            assertTrue(FrontierContainerSocketPlan.support(state, surface).isPresent());
            assertEquals("container.production-station", ReferenceContainerCustody.semanticKind(state, container.id()));
            assertEquals(container.ownerId(), state.bootstrap().settlements().stream()
                    .filter(settlement -> settlement.structures().stream()
                            .anyMatch(structure -> structure.id().equals(station.facilityId())))
                    .findFirst().orElseThrow().id());
        }
        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(state.inventory().containers(), restored.inventory().containers());
        assertEquals(state.inventory().surfaces(), restored.inventory().surfaces());
    }

    @Test
    void stationCannotClaimAnotherContainerOrOverlapItsActor() {
        SubjectId facility = new SubjectId("structure:bakery");
        SubjectId container = new SubjectId("container:bakery-station");
        SubjectId owner = new SubjectId("settlement:one");
        ProductionStationSpec station = new ProductionStationSpec(new SubjectId("station:bakery"), facility,
                container, ProductionStationSpec.Capability.BAKING,
                SurfaceAnchor.at(1, 64, 1), SurfaceAnchor.at(2, 64, 1), 0, 1);
        assertThrows(IllegalArgumentException.class, () -> new ContainerRecord(
                new SubjectId("container:other"), owner, 27, Optional.of(station)));
        assertThrows(IllegalArgumentException.class, () -> new ContainerRecord(container, owner, 1, Optional.of(station)));
        assertThrows(IllegalArgumentException.class, () -> new ProductionStationSpec(station.id(), facility, container,
                station.capability(), station.workerStation(), station.workerStation(), 0, 1));
    }

    @Test
    void loadingOrderNamesTheCurrentInputPortAndRejectsWrongMachine() {
        FrontierWorldState state = FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:bakery-order"), 91L));
        ProductionStationSpec machine = state.inventory().containers().values().stream()
                .flatMap(container -> container.productionStation().stream()).findFirst().orElseThrow();
        SubjectId actor = new SubjectId("resident:test-baker");
        ActorContainerItemOrder.Portion.Fungible wheat = new ActorContainerItemOrder.Portion.Fungible(
                new SubjectId("custody:test-baker"), new ResourceCustody.Actor(actor),
                new SubjectId("custody:test-station"), new ResourceCustody.Container(machine.containerId()),
                Optional.empty(), "minecraft:wheat", Map.of(new SubjectId("lot:test-wheat"), 64));
        ActorContainerItemOrder load = new ActorContainerItemOrder(new SubjectId("job:test-bread"), actor,
                ActorContainerItemOrder.Direction.PLACE, wheat,
                new ActorContainerItemOrder.ContainerEndpoint.FungibleStation(machine, ActorContainerItemOrder.StationPort.INPUT),
                machine.workerStation(), ActorContainerItemOrder.Hand.MAIN, 1, 1);
        load.requireCurrentStation(state.inventory());
        assertThrows(IllegalArgumentException.class, () -> new ActorContainerItemOrder(load.ownerId(), actor,
                load.direction(), wheat,
                new ActorContainerItemOrder.ContainerEndpoint.FungibleStation(machine, ActorContainerItemOrder.StationPort.OUTPUT),
                machine.workerStation(), load.hand(), load.goalOrdinal(), load.goalRevision()));
        ProductionStationSpec foreign = new ProductionStationSpec(machine.id(), new SubjectId("structure:foreign"),
                machine.containerId(), machine.capability(), machine.workerStation(), machine.socketSurface(),
                machine.inputSlot(), machine.outputSlot());
        ActorContainerItemOrder forged = new ActorContainerItemOrder(load.ownerId(), actor, load.direction(), wheat,
                new ActorContainerItemOrder.ContainerEndpoint.FungibleStation(foreign, ActorContainerItemOrder.StationPort.INPUT),
                machine.workerStation(), load.hand(), load.goalOrdinal(), load.goalRevision());
        assertThrows(IllegalArgumentException.class, () -> forged.requireCurrentStation(state.inventory()));
    }

    @Test
    void coldStationLoadingRequiresArrivalAndNoCurrentPhysicalContainerOwner() {
        FrontierWorldState initial = FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:bakery-cold-loading"), 91L));
        ContainerRecord stationContainer = initial.inventory().containers().values().stream()
                .filter(container -> container.productionStation().isPresent()).findFirst().orElseThrow();
        ProductionStationSpec machine = stationContainer.productionStation().orElseThrow();
        SubjectId actorId = initial.bootstrap().settlements().stream()
                .filter(settlement -> settlement.id().equals(stationContainer.ownerId()))
                .findFirst().orElseThrow().residents().getFirst().id();
        SubjectId actorAccount = new SubjectId("custody:bakery-loading-actor");
        SubjectId stationAccount = new SubjectId("custody:bakery-loading-station");
        SubjectId wheatId = new SubjectId("lot:bakery-loading-wheat");
        ResourceLot wheat = new ResourceLot(wheatId, stationContainer.ownerId(), "minecraft:wheat", 64,
                "fixture:bakery-loading", java.util.List.of());
        FungibleResourceLedger resources = initial.inventory().fungibleResources().issue(wheat,
                new CustodyAccount(actorAccount, new ResourceCustody.Actor(actorId), Map.of(wheatId, 64), Map.of()));
        java.util.Map<SubjectId, ActorLocation> actors = new java.util.LinkedHashMap<>(initial.actorLocations());
        actors.put(actorId, ActorLocation.standingOn(machine.workerStation()));
        FrontierWorldState atStation = initial.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(initial.inventory().withFungibleResources(resources)).actorLocations(actors));
        ActorContainerItemOrder order = new ActorContainerItemOrder(new SubjectId("job:bakery-loading"), actorId,
                ActorContainerItemOrder.Direction.PLACE,
                new ActorContainerItemOrder.Portion.Fungible(actorAccount, new ResourceCustody.Actor(actorId),
                        stationAccount, new ResourceCustody.Container(machine.containerId()), Optional.empty(),
                        "minecraft:wheat", Map.of(wheatId, 64)),
                new ActorContainerItemOrder.ContainerEndpoint.FungibleStation(machine, ActorContainerItemOrder.StationPort.INPUT),
                machine.workerStation(), ActorContainerItemOrder.Hand.MAIN, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> ActorItemCustody.transferCold(initial.withInventory(
                initial.inventory().withFungibleResources(resources)), order), "a remote actor cannot load a station");
        FrontierWorldState loaded = atStation.withInventory(ActorItemCustody.transferCold(atStation, order));
        assertEquals(Map.of(wheatId, 64), loaded.inventory().fungibleResources().accounts()
                .get(stationAccount).lotQuantities());
        FrontierWorldState physicallyHeld = ReferenceContainerCustodyFixtures.observedAndHeld(atStation, machine.containerId());
        assertThrows(IllegalArgumentException.class, () -> ActorItemCustody.transferCold(physicallyHeld, order),
                "the loaded machine cannot have a concurrent abstract inventory writer");
    }
}
