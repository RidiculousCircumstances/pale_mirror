package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceStorageAdoptionTest {
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
}
