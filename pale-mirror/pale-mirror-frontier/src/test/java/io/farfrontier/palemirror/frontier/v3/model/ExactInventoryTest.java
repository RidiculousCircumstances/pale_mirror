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
        ExactItemStack storedStack = new ExactItemStack(stored, owner, "minecraft:iron_ingot", 64, new InventoryCustody.ContainerSlot(container, 0));
        ExactItemStack cargoStack = new ExactItemStack(transported, owner, "minecraft:bread", 8, new InventoryCustody.Cargo(cargo));
        assertDoesNotThrow(() -> new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 27)),
                Map.of(stored, storedStack, transported, cargoStack), Map.of(cargo, new CargoBatch(cargo, owner, List.of(transported))), Map.of(), Map.of(), Map.of(), surfaceFor(container)));
        assertThrows(IllegalArgumentException.class, () -> new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 27)),
                Map.of(stored, storedStack), Map.of(), Map.of(player, List.of(stored)), Map.of(), Map.of(), surfaceFor(container)));
        assertThrows(IllegalArgumentException.class, () -> new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 27)),
                Map.of(stored, storedStack, new SubjectId("item:duplicate"),
                        new ExactItemStack(new SubjectId("item:duplicate"), owner, "minecraft:stone", 1, new InventoryCustody.ContainerSlot(container, 0))),
                Map.of(), Map.of(), Map.of(), Map.of(), surfaceFor(container)));
        assertThrows(IllegalArgumentException.class, () -> new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 27)),
                Map.of(new SubjectId("item:wrong-key"), storedStack), Map.of(), Map.of(), Map.of(), Map.of(), surfaceFor(container)));
    }

    @Test
    void worldCarrierCustodyRequiresTheExactCarrierReverseIndex() {
        SubjectId item = new SubjectId("item:carrier-bread");
        UUID carrier = UUID.fromString("00000000-0000-0000-0000-000000000042");
        ExactItemStack stack = new ExactItemStack(item, new SubjectId("hive:frontier"), "minecraft:bread", 64, new InventoryCustody.WorldCarrier(carrier));

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
                Map.of(input, new ExactItemStack(input, owner, "minecraft:wheat", 64, new InventoryCustody.ContainerSlot(container, 0))), Map.of(), Map.of(), Map.of(), Map.of(), surfaceFor(container));

        ExactInventory stored = inventory.withoutItem(input).store(new ExactItemStack(output, owner, "minecraft:bread", 64, new InventoryCustody.ContainerSlot(container, 0)));
        assertEquals(output, stored.itemAt(container, 0).orElseThrow().id());
        assertThrows(IllegalArgumentException.class, () -> inventory.store(new ExactItemStack(output, owner, "minecraft:bread", 64, new InventoryCustody.ContainerSlot(container, 0))));
    }

    @Test
    void derivedSlotIndexFindsOnlyItsExactStackAndRejectsForgedIndex() {
        SubjectId container = new SubjectId("container:indexed");
        SubjectId owner = new SubjectId("settlement:one");
        SubjectId item = new SubjectId("item:indexed");
        InventoryCustody.ContainerSlot occupied = new InventoryCustody.ContainerSlot(container, 7);
        ExactItemStack stack = new ExactItemStack(item, owner, "minecraft:iron_ingot", 64, occupied);
        Map<SubjectId, ContainerRecord> containers = Map.of(container, new ContainerRecord(container, owner, 9));
        Map<SubjectId, ContainerSurface> surfaces = surfaceFor(container);

        ExactInventory inventory = new ExactInventory(containers, Map.of(item, stack), Map.of(), Map.of(), Map.of(), Map.of(), surfaces);

        assertEquals(item, inventory.itemAt(container, 7).orElseThrow().id());
        assertEquals(java.util.Optional.empty(), inventory.itemAt(container, 8));
        assertThrows(IllegalArgumentException.class, () -> new ExactInventory(containers, Map.of(item, stack), Map.of(), Map.of(), Map.of(), Map.of(), surfaces,
                Map.of(new InventoryCustody.ContainerSlot(container, 8), item)));
    }

    @Test
    void loadingCargoMovesTheSameExactStackWithoutDuplicatingIt() {
        SubjectId container = new SubjectId("container:depot");
        SubjectId owner = new SubjectId("settlement:one");
        SubjectId item = new SubjectId("item:bread");
        SubjectId cargo = new SubjectId("cargo:supply");
        ExactInventory inventory = new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 2)),
                Map.of(item, new ExactItemStack(item, owner, "minecraft:bread", 64, new InventoryCustody.ContainerSlot(container, 0))), Map.of(), Map.of(), Map.of(), Map.of(), surfaceFor(container));

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
                Map.of(item, new ExactItemStack(item, owner, "minecraft:bread", 8, slot)), Map.of(), Map.of(), Map.of(), Map.of(), surfaceFor(container));

        ExactInventory withdrawn = stored.moveObservedItem(item, slot, new InventoryCustody.Player(player));
        assertEquals(new InventoryCustody.Player(player), withdrawn.items().get(item).custody());
        assertEquals(List.of(item), withdrawn.playerItems().get(player));
        ExactInventory returned = withdrawn.moveObservedItem(item, new InventoryCustody.Player(player), slot);
        assertEquals(slot, returned.items().get(item).custody());
        assertThrows(IllegalArgumentException.class, () -> stored.moveObservedItem(item, slot, new InventoryCustody.ContainerSlot(container, 3)));
    }

    @Test
    void observedWorldCarrierTransferKeepsTheSameExactStackAndReverseIndex() {
        SubjectId container = new SubjectId("container:store");
        SubjectId item = new SubjectId("item:carrier-bread");
        InventoryCustody.ContainerSlot slot = new InventoryCustody.ContainerSlot(container, 3);
        UUID carrier = UUID.fromString("00000000-0000-0000-0000-000000000043");
        ExactInventory stored = new ExactInventory(Map.of(container, new ContainerRecord(container, new SubjectId("hive:frontier"), 9)),
                Map.of(item, new ExactItemStack(item, new SubjectId("hive:frontier"), "minecraft:bread", 8, slot)), Map.of(), Map.of(), Map.of(), Map.of(), surfaceFor(container));

        ExactInventory carried = stored.moveObservedItem(item, slot, new InventoryCustody.WorldCarrier(carrier));
        assertEquals(new InventoryCustody.WorldCarrier(carrier), carried.items().get(item).custody());
        assertEquals(new SubjectId("hive:frontier"), carried.items().get(item).economicOwnerId(), "physical transport must not silently change the economic claim");
        assertEquals(List.of(item), carried.worldCarrierItems().get(carrier));
        ExactInventory returned = carried.moveObservedItem(item, new InventoryCustody.WorldCarrier(carrier), slot);
        assertEquals(slot, returned.items().get(item).custody());
        InventoryCustody.Player player = new InventoryCustody.Player(UUID.fromString("00000000-0000-0000-0000-000000000044"));
        assertDoesNotThrow(() -> new ExactItemCustodyChanged(item, player, new InventoryCustody.WorldCarrier(carrier)));
        assertThrows(IllegalArgumentException.class, () -> new ExactItemCustodyChanged(item, player,
                new InventoryCustody.Player(UUID.fromString("00000000-0000-0000-0000-000000000045"))));
    }

    @Test
    void observedActorTransferRetainsTheSameExactStackWithoutASecondEquipmentLedger() {
        SubjectId container = new SubjectId("container:armory");
        SubjectId actor = new SubjectId("resident:armory-guard");
        SubjectId item = new SubjectId("item:guard-sword");
        InventoryCustody.ContainerSlot slot = new InventoryCustody.ContainerSlot(container, 0);
        ExactInventory stored = new ExactInventory(Map.of(container, new ContainerRecord(container, new SubjectId("settlement:one"), 2)),
                Map.of(item, new ExactItemStack(item, new SubjectId("settlement:one"), "minecraft:iron_sword", 1, slot)), Map.of(), Map.of(), Map.of(), Map.of(), surfaceFor(container));

        ExactInventory equipped = stored.moveObservedItem(item, slot, new InventoryCustody.Actor(actor));

        assertEquals(new InventoryCustody.Actor(actor), equipped.items().get(item).custody());
        assertEquals(List.of(item), equipped.actorItems(actor).stream().map(ExactItemStack::id).toList());
        assertThrows(IllegalArgumentException.class, () -> new InventoryCustody.Actor(new SubjectId("structure:armory")));
    }

    @Test
    void cargoRetainsTheSenderClaimUntilObservedReceiptTransfersItToTheReceiver() {
        SubjectId senderContainer = new SubjectId("container:sender");
        SubjectId receiverContainer = new SubjectId("container:receiver");
        SubjectId sender = new SubjectId("settlement:one");
        SubjectId receiver = new SubjectId("hive:frontier");
        SubjectId item = new SubjectId("item:claimed-bread");
        SubjectId cargo = new SubjectId("cargo:claimed-bread");
        ExactInventory inventory = new ExactInventory(
                Map.of(senderContainer, new ContainerRecord(senderContainer, sender, 2), receiverContainer, new ContainerRecord(receiverContainer, receiver, 2)),
                Map.of(item, new ExactItemStack(item, sender, "minecraft:bread", 8, new InventoryCustody.ContainerSlot(senderContainer, 0))),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(
                        senderContainer, new ContainerSurface(senderContainer, new BlockPosition(0, 64, 0), ContainerSurfaceStatus.ACTIVE),
                        receiverContainer, new ContainerSurface(receiverContainer, new BlockPosition(4, 64, 0), ContainerSurfaceStatus.ACTIVE)));

        ExactInventory loaded = inventory.loadCargo(new CargoBatch(cargo, sender, List.of(item)));
        assertEquals(sender, loaded.items().get(item).economicOwnerId());
        ExactInventory received = loaded.completeCargoHandoff(cargo, List.of(new CargoHandoffPlacement(item, new InventoryCustody.ContainerSlot(receiverContainer, 1))));
        assertEquals(receiver, received.items().get(item).economicOwnerId());
        assertEquals(new InventoryCustody.ContainerSlot(receiverContainer, 1), received.items().get(item).custody());
    }

    @Test
    void physicalInventoryConflictIsBoundedIdempotentEvidenceRatherThanAnInventoryRepair() {
        SubjectId container = new SubjectId("container:store");
        SubjectId owner = new SubjectId("hive:frontier");
        SubjectId item = new SubjectId("item:bread");
        InventoryCustody.ContainerSlot slot = new InventoryCustody.ContainerSlot(container, 3);
        ExactInventory inventory = new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 9)),
                Map.of(item, new ExactItemStack(item, owner, "minecraft:bread", 8, slot)), Map.of(), Map.of(), Map.of(), Map.of(), surfaceFor(container));
        InventoryConflict conflict = new InventoryConflict(new SubjectId("conflict:inventory-bread"), item, container, 3, InventoryConflictKind.MISSING);

        ExactInventory observed = inventory.recordConflict(conflict);
        assertEquals(conflict, observed.conflicts().get(conflict.id()));
        assertEquals(observed, observed.recordConflict(conflict));
        assertEquals(slot, observed.items().get(item).custody(), "conflict evidence must not repair or move the canonical item");
        ExactInventory consumed = observed.consume(item, 8);
        assertEquals(conflict, consumed.conflicts().get(conflict.id()),
                "durable conflict evidence remains anchored at its physical container after its original exact subject retires");
        assertThrows(IllegalArgumentException.class, () -> observed.recordConflict(new InventoryConflict(conflict.id(), item, container, 3, InventoryConflictKind.FOREIGN_OR_DUPLICATE)));
    }

    private static Map<SubjectId, ContainerSurface> surfaceFor(SubjectId container) {
        return Map.of(container, new ContainerSurface(container, new BlockPosition(0, 64, 0), ContainerSurfaceStatus.UNMATERIALIZED));
    }
}
