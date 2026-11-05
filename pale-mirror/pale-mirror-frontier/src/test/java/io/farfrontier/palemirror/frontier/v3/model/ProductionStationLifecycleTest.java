package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionStationLifecycleTest {
    @Test void suspensionPreservesPendingPhysicalEffectIdentityAndCannotResumeOrAwardWork() {
        var station = new SubjectId("station:pending-suspension"); var job = new SubjectId("job:pending-suspension");
        var operation = new ProductionOperation(new ProductionOperation.Id(new SubjectId("operation:pending-suspension")), station, job,
                BakeryRecipes.BREAD, new ProductionOperation.ExactInput(new SubjectId("item:pending-input")), new SubjectId("item:pending-output"),
                3, 1, Optional.of(WorkProgress.pending(100, 10).resume(10, 1_000, 110)), false,
                new ProductionOperation.OrderInitiator(ProductionOrderKind.STATION_WORK, job),
                Optional.of(new ProductionStationClock.Attendance(1_000, 110)));
        var machine = ProductionStations.Machine.installed(station).start(operation);
        var burn = new ProductionStationFuelBurn(station, new SubjectId("container:pending"), new SubjectId("account:pending"),
                machine.revision(), 0, "minecraft:coal", Map.of(new SubjectId("lot:pending"), 1), 1_600, List.of(), 10);
        var pendingFuel = machine.prepareFuel(burn);
        var paused = pendingFuel.suspendAttendance(operation, operation.progress().orElseThrow().pause(20));
        assertEquals(pendingFuel.revision(), paused.revision()); assertEquals(pendingFuel.pendingFuel(), paused.pendingFuel());
        assertTrue(paused.operation().orElseThrow().attendance().isEmpty());
        assertEquals(10_000, paused.operation().orElseThrow().progress().orElseThrow().completedAt(1_000));
        assertThrows(IllegalArgumentException.class, () -> pendingFuel.replace(operation, paused.operation().orElseThrow()));
        assertThrows(IllegalArgumentException.class, () -> pendingFuel.suspendAttendance(operation,
                new WorkProgress(100_000, 11_000, 20, 0, 20)), "suspension cannot award invented work");
        var complete = operation.withProgress(operation.progress().orElseThrow().pause(110));
        var ready = machine.replace(operation, complete);
        var boundary = new ProductionStationRecipeBoundary(station, burn.containerId(), operation.id(), ready.revision(), 0, List.of(), List.of());
        var pendingRecipe = ready.prepareRecipe(boundary);
        var settled = pendingRecipe.suspendAttendance(complete, complete.progress().orElseThrow().pause(120));
        assertEquals(pendingRecipe.revision(), settled.revision()); assertEquals(pendingRecipe.pendingRecipe(), settled.pendingRecipe());
        assertTrue(settled.operation().orElseThrow().complete()); assertTrue(settled.operation().orElseThrow().attendance().isEmpty());
        assertTrue(settled.completeRecipe(settled.operation().orElseThrow()).operation().orElseThrow().transformed());
    }
    @Test void anIdleMachineCannotReuseAValidationAfterItsInstallationDisappears() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:machine-reads"), 41,
                FrontierRulesets.fuelledQuarryGraybox()));
        var machines = state.productionStations();
        var catalog = state.bootstrap().ruleset().production();
        var tools = state.bootstrap().ruleset().tools();
        machines.validate(state.inventory(), catalog, tools);
        assertThrows(IllegalArgumentException.class, () -> machines.validate(ExactInventory.empty(), catalog, tools));
        machines.validate(state.inventory(), catalog, tools);
    }
    @Test void replacementHasNewIdentityAndNoInheritedHeatWhileObservedPortStockIsAdoptedOnlyOnce() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:station-replacement"), 41,
                FrontierRulesets.fuelledQuarryGraybox()));
        var old = state.inventory().containers().values().stream().filter(value -> value.productionStation().isPresent())
                .sorted(Comparator.comparing(ContainerRecord::id)).findFirst().orElseThrow();
        var original = old.productionStation().orElseThrow();
        state = state.withChanges(FrontierWorldStateUpdate.begin().productionStations(state.productionStations().with(
                new ProductionStations.Machine(original.id(), Optional.of(new FuelCombustion("minecraft:coal", 1, 1601)), Optional.empty(), 5, Optional.empty()))));
        var spec = new ProductionStationSpec(new SubjectId("station:replacement-test"), original.facilityId(), new SubjectId("container:replacement-test"),
                original.equipmentType(), original.capability(), original.workplaceId(), original.workerStation(), original.socketSurface(), original.facing(), original.inputSlots(), original.outputSlot(),
                original.fuelSlot(), original.maintenanceSlot());
        var wheat = new ResourceLot(new SubjectId("lot:replacement-input"), old.ownerId(), "minecraft:wheat", 9, "fixture:player-input", List.of());
        var record = new ContainerRecord(spec.containerId(), old.ownerId(), spec.inventorySize(), ContainerPurpose.PRODUCTION_STATION, Optional.of(spec), Optional.empty(),
                new FungibleContainerLayout(Map.of(0, new FungibleContainerLayout.Stack("minecraft:wheat", 9))));
        var install = new ProductionStationInstalled(record, state.inventory().surfaces().get(old.id()).position(), List.of(new FixedContainerAdoption.PlacedResource(0, wheat)));
        var active = state;
        assertThrows(IllegalArgumentException.class, () -> install.apply(active, spec.id(), BakeryStationInstallation::installed));
        state = state.withInventory(state.inventory().withSurfaceStatus(old.id(), ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(old.id(), ContainerSurfaceStatus.ACTIVE).withSurfaceStatus(old.id(), ContainerSurfaceStatus.RETIRED));
        var terminal = io.farfrontier.palemirror.frontier.v3.process.ProductionStationProgressObligation.describe(
                state, state.productionStations().require(original.id()));
        assertEquals(io.farfrontier.palemirror.frontier.v3.process.ProductionStationProgressObligation.Disposition.TERMINAL,
                terminal.disposition());
        assertEquals(io.farfrontier.palemirror.frontier.v3.process.ProductionStationProgressObligation.Next.CLOSED, terminal.next());
        var retiredCodec = new FrontierWorldStateCodec();
        assertEquals(terminal, io.farfrontier.palemirror.frontier.v3.process.ProductionStationProgressObligation.describe(
                retiredCodec.decode(retiredCodec.encode(state)), state.productionStations().require(original.id())));
        state = new ProductionStationRetired(original.id(), 5).apply(state, original.id());
        var priorInventory = state.inventory();
        assertThrows(IllegalArgumentException.class, () -> ProductionStationDirectory.require(priorInventory, spec.id()));
        var replaced = install.apply(state, spec.id(), BakeryStationInstallation::installed);
        assertEquals(record, ProductionStationDirectory.require(replaced.inventory(), spec.id()));
        assertTrue(ProductionStationDirectory.inFacility(replaced.inventory(), spec.facilityId()).contains(record));
        assertEquals(replaced.inventory().containers().values().stream().filter(value ->
                value.ownerId().equals(record.ownerId()) && value.productionStation().isPresent()).toList(),
                ProductionStationDirectory.owned(replaced.inventory(), record.ownerId()));
        assertFalse(ProductionStationDirectory.owned(priorInventory, record.ownerId()).contains(record),
                "the owner directory cannot lend a replacement to the previous inventory");
        assertThrows(IllegalArgumentException.class, () -> ProductionStationDirectory.require(priorInventory, spec.id()),
                "a derived lookup cannot lend the replacement to a predecessor inventory");
        assertFalse(replaced.productionStations().machines().containsKey(original.id()));
        assertEquals(0, replaced.productionStations().require(spec.id()).burnRemaining(100));
        assertTrue(replaced.productionStations().require(spec.id()).operation().isEmpty());
        assertEquals(9, replaced.inventory().fungibleResources().lots().get(wheat.id()).quantity());
        assertEquals(ContainerSurfaceStatus.ADOPTING, replaced.inventory().surfaces().get(record.id()).status());
        var codec = new FrontierWorldStateCodec(); assertEquals(replaced, codec.decode(codec.encode(replaced)));
        var wal = FrontierWorldPayloadCodecs.create(); assertEquals(install, wal.decode(install.type(), wal.encode(install)));
        assertThrows(IllegalArgumentException.class, () -> install.apply(replaced, spec.id(), BakeryStationInstallation::installed));
        var confirmed = replaced.withInventory(replaced.inventory().withSurfaceStatus(record.id(), ContainerSurfaceStatus.ACTIVE));
        assertEquals(Optional.of(spec), ProductionStationSelection.usable(confirmed, spec.facilityId(), BakeryRecipes.BREAD));
    }
}
