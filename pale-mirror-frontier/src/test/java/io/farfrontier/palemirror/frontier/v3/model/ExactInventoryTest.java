package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExactInventoryTest {
    @Test
    void containerSurfaceCanOnlyAdvanceDurablyOrBecomeAVisibleConflict() {
        SubjectId container = new SubjectId("container:surface");
        Map<SubjectId, ContainerRecord> containers = Map.of(container, new ContainerRecord(container, new SubjectId("settlement:one"), 27));
        Map<SubjectId, ContainerSurface> surfaces = Map.of(container,
                new ContainerSurface(container, new BlockPosition(8, 65, 8), ContainerSurfaceStatus.UNMATERIALIZED));
        ExactInventory initial = new ExactInventory(containers, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), surfaces);

        ExactInventory prepared = initial.withSurfaceStatus(container, ContainerSurfaceStatus.PREPARED);
        ExactInventory active = prepared.withSurfaceStatus(container, ContainerSurfaceStatus.ACTIVE);
        assertEquals(ContainerSurfaceStatus.ACTIVE, active.surfaces().get(container).status());
        assertEquals(ContainerSurfaceStatus.CONFLICT, active.withSurfaceStatus(container, ContainerSurfaceStatus.CONFLICT).surfaces().get(container).status());
        assertThrows(IllegalArgumentException.class, () -> initial.withSurfaceStatus(container, ContainerSurfaceStatus.ACTIVE));
        assertThrows(IllegalArgumentException.class, () -> active.withSurfaceStatus(container, ContainerSurfaceStatus.PREPARED));
        assertThrows(IllegalArgumentException.class, () -> new ExactInventory(containers, Map.of(), Map.of(), Map.of()));
    }

    @Test
    void eachExactStackHasOneValidatedCustody() {
        SubjectId container = new SubjectId("container:depot");
        SubjectId owner = new SubjectId("settlement:one");
        SubjectId stored = new SubjectId("item:stored");
        SubjectId transported = new SubjectId("item:transported");
        SubjectId cargo = new SubjectId("cargo:one");
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ExactItemStack storedStack = new ExactItemStack(stored, "minecraft:iron_ingot", 64, new InventoryCustody.ContainerSlot(container, 0));
        ExactItemStack cargoStack = new ExactItemStack(transported, "minecraft:bread", 8, new InventoryCustody.Cargo(cargo));
        assertDoesNotThrow(() -> new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 27)),
                Map.of(stored, storedStack, transported, cargoStack), Map.of(cargo, new CargoBatch(cargo, owner, List.of(transported))), Map.of(), Map.of(), Map.of(), surfaceFor(container)));
        assertThrows(IllegalArgumentException.class, () -> new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 27)),
                Map.of(stored, storedStack), Map.of(), Map.of(player, List.of(stored)), Map.of(), Map.of(), surfaceFor(container)));
        assertThrows(IllegalArgumentException.class, () -> new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 27)),
                Map.of(stored, storedStack, new SubjectId("item:duplicate"),
                        new ExactItemStack(new SubjectId("item:duplicate"), "minecraft:stone", 1, new InventoryCustody.ContainerSlot(container, 0))),
                Map.of(), Map.of(), Map.of(), Map.of(), surfaceFor(container)));
        assertThrows(IllegalArgumentException.class, () -> new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 27)),
                Map.of(new SubjectId("item:wrong-key"), storedStack), Map.of(), Map.of(), Map.of(), Map.of(), surfaceFor(container)));
    }

    @Test
    void worldCarrierCustodyRequiresTheExactCarrierReverseIndex() {
        SubjectId item = new SubjectId("item:carrier-bread");
        UUID carrier = UUID.fromString("00000000-0000-0000-0000-000000000042");
        ExactItemStack stack = new ExactItemStack(item, "minecraft:bread", 64, new InventoryCustody.WorldCarrier(carrier));

        ExactInventory inventory = new ExactInventory(Map.of(), Map.of(item, stack), Map.of(), Map.of(), Map.of(carrier, List.of(item)));
        assertEquals(new InventoryCustody.WorldCarrier(carrier), inventory.items().get(item).custody());
        assertThrows(IllegalArgumentException.class, () -> new ExactInventory(Map.of(), Map.of(item, stack), Map.of(), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ExactInventory(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(carrier, List.of(item))));
        assertThrows(IllegalArgumentException.class, () -> new ExactInventory(Map.of(), Map.of(item, stack), Map.of(), Map.of(), Map.of(carrier, List.of(item, item))));
    }

    @Test
    void productionStorageCannotOverwriteARealExactStack() {
        SubjectId container = new SubjectId("container:depot");
        SubjectId owner = new SubjectId("settlement:one");
        SubjectId input = new SubjectId("item:input");
        SubjectId output = new SubjectId("item:output");
        ExactInventory inventory = new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 2)),
                Map.of(input, new ExactItemStack(input, "minecraft:wheat", 64, new InventoryCustody.ContainerSlot(container, 0))), Map.of(), Map.of(), Map.of(), Map.of(), surfaceFor(container));

        ExactInventory stored = inventory.withoutItem(input).store(new ExactItemStack(output, "minecraft:bread", 64, new InventoryCustody.ContainerSlot(container, 0)));
        assertEquals(output, stored.itemAt(container, 0).orElseThrow().id());
        assertThrows(IllegalArgumentException.class, () -> inventory.store(new ExactItemStack(output, "minecraft:bread", 64, new InventoryCustody.ContainerSlot(container, 0))));
    }

    @Test
    void loadingCargoMovesTheSameExactStackWithoutDuplicatingIt() {
        SubjectId container = new SubjectId("container:depot");
        SubjectId owner = new SubjectId("settlement:one");
        SubjectId item = new SubjectId("item:bread");
        SubjectId cargo = new SubjectId("cargo:supply");
        ExactInventory inventory = new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 2)),
                Map.of(item, new ExactItemStack(item, "minecraft:bread", 64, new InventoryCustody.ContainerSlot(container, 0))), Map.of(), Map.of(), Map.of(), Map.of(), surfaceFor(container));

        ExactInventory loaded = inventory.loadCargo(new CargoBatch(cargo, owner, List.of(item)));

        assertEquals(new InventoryCustody.Cargo(cargo), loaded.items().get(item).custody());
        assertEquals(List.of(item), loaded.cargo().get(cargo).itemIds());
        assertThrows(IllegalArgumentException.class, () -> loaded.loadCargo(new CargoBatch(new SubjectId("cargo:again"), owner, List.of(item))));
        assertThrows(IllegalArgumentException.class, () -> inventory.loadCargo(new CargoBatch(cargo, new SubjectId("settlement:other"), List.of(item))));
    }

    @Test
    void observedPlayerTransferMovesOneExactStackWithoutAdoptingOrDuplicatingIt() {
        SubjectId container = new SubjectId("container:store");
        SubjectId owner = new SubjectId("hive:frontier");
        SubjectId item = new SubjectId("item:bread");
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000012");
        InventoryCustody.ContainerSlot slot = new InventoryCustody.ContainerSlot(container, 3);
        ExactInventory stored = new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 9)),
                Map.of(item, new ExactItemStack(item, "minecraft:bread", 8, slot)), Map.of(), Map.of(), Map.of(), Map.of(), surfaceFor(container));

        ExactInventory withdrawn = stored.moveObservedItem(item, slot, new InventoryCustody.Player(player));
        assertEquals(new InventoryCustody.Player(player), withdrawn.items().get(item).custody());
        assertEquals(List.of(item), withdrawn.playerItems().get(player));
        ExactInventory returned = withdrawn.moveObservedItem(item, new InventoryCustody.Player(player), slot);
        assertEquals(slot, returned.items().get(item).custody());
        assertThrows(IllegalArgumentException.class, () -> stored.moveObservedItem(item, slot, new InventoryCustody.ContainerSlot(container, 3)));
    }

    @Test
    void physicalInventoryConflictIsBoundedIdempotentEvidenceRatherThanAnInventoryRepair() {
        SubjectId container = new SubjectId("container:store");
        SubjectId owner = new SubjectId("hive:frontier");
        SubjectId item = new SubjectId("item:bread");
        InventoryCustody.ContainerSlot slot = new InventoryCustody.ContainerSlot(container, 3);
        ExactInventory inventory = new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 9)),
                Map.of(item, new ExactItemStack(item, "minecraft:bread", 8, slot)), Map.of(), Map.of(), Map.of(), Map.of(), surfaceFor(container));
        InventoryConflict conflict = new InventoryConflict(new SubjectId("conflict:inventory-bread"), item, container, 3, InventoryConflictKind.MISSING);

        ExactInventory observed = inventory.recordConflict(conflict);
        assertEquals(conflict, observed.conflicts().get(conflict.id()));
        assertEquals(observed, observed.recordConflict(conflict));
        assertEquals(slot, observed.items().get(item).custody(), "conflict evidence must not repair or move the canonical item");
        assertThrows(IllegalArgumentException.class, () -> observed.recordConflict(new InventoryConflict(conflict.id(), item, container, 3, InventoryConflictKind.FOREIGN_OR_DUPLICATE)));
    }

    private static Map<SubjectId, ContainerSurface> surfaceFor(SubjectId container) {
        return Map.of(container, new ContainerSurface(container, new BlockPosition(0, 64, 0), ContainerSurfaceStatus.UNMATERIALIZED));
    }
}
