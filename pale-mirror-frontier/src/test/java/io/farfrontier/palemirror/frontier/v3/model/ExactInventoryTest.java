package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExactInventoryTest {
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
                Map.of(stored, storedStack, transported, cargoStack), Map.of(cargo, new CargoBatch(cargo, owner, List.of(transported))), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 27)),
                Map.of(stored, storedStack), Map.of(), Map.of(player, List.of(stored))));
        assertThrows(IllegalArgumentException.class, () -> new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 27)),
                Map.of(stored, storedStack, new SubjectId("item:duplicate"), new ExactItemStack(new SubjectId("item:duplicate"), "minecraft:stone", 1, new InventoryCustody.ContainerSlot(container, 0))), Map.of(), Map.of()));
    }
}
