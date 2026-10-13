package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionStationLifecycleTest {
    @Test void replacementHasNewIdentityAndNoInheritedHeatWhileObservedPortStockIsAdoptedOnlyOnce() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:station-replacement"), 41,
                FrontierRulesets.fuelledQuarryGraybox()));
        var old = state.inventory().containers().values().stream().filter(value -> value.productionStation().isPresent())
                .sorted(Comparator.comparing(ContainerRecord::id)).findFirst().orElseThrow();
        var original = old.productionStation().orElseThrow();
        state = state.withChanges(FrontierWorldStateUpdate.begin().productionStations(state.productionStations().with(
                new ProductionStations.Machine(original.id(), 3200, Optional.empty(), 5, Optional.empty()))));
        var spec = new ProductionStationSpec(new SubjectId("station:replacement-test"), original.facilityId(), new SubjectId("container:replacement-test"),
                original.equipmentType(), original.capability(), original.workerStation(), original.socketSurface(), 0, 1, 2);
        var wheat = new ResourceLot(new SubjectId("lot:replacement-input"), old.ownerId(), "minecraft:wheat", 9, "fixture:player-input", List.of());
        var record = new ContainerRecord(spec.containerId(), old.ownerId(), 3, ContainerPurpose.PRODUCTION_STATION, Optional.of(spec), Optional.empty(),
                new FungibleContainerLayout(Map.of(0, new FungibleContainerLayout.Stack("minecraft:wheat", 9))));
        var install = new ProductionStationInstalled(record, state.inventory().surfaces().get(old.id()).position(), List.of(wheat));
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
        var replaced = install.apply(state, spec.id(), BakeryStationInstallation::installed);
        assertFalse(replaced.productionStations().machines().containsKey(original.id()));
        assertEquals(0, replaced.productionStations().require(spec.id()).storedHeat());
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
