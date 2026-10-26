package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContainerCompartmentTest {
    @Test void inputReorderingIsEquivalentButMovingTheSameWoodIntoFuelChangesItsMeaning() {
        var layout = new ContainerCompartmentLayout(4, Map.of(ContainerSectionId.INPUT, Set.of(0, 1),
                ContainerSectionId.OUTPUT, Set.of(2), ContainerSectionId.FUEL, Set.of(3)));
        var empty = MaterialContainerImage.Slot.empty(2);
        var original = List.of(MaterialContainerImage.Slot.fungible(0, "minecraft:oak_log", 2),
                MaterialContainerImage.Slot.fungible(1, "minecraft:stick", 4), empty,
                MaterialContainerImage.Slot.fungible(3, "minecraft:oak_log", 1));
        var reordered = List.of(MaterialContainerImage.Slot.fungible(0, "minecraft:stick", 4),
                MaterialContainerImage.Slot.fungible(1, "minecraft:oak_log", 2), empty,
                MaterialContainerImage.Slot.fungible(3, "minecraft:oak_log", 1));
        var fuelled = List.of(MaterialContainerImage.Slot.fungible(0, "minecraft:oak_log", 1),
                MaterialContainerImage.Slot.fungible(1, "minecraft:stick", 4), empty,
                MaterialContainerImage.Slot.fungible(3, "minecraft:oak_log", 2));
        var first = MaterialContainerImage.fingerprint("machine", "container:test", layout, original);
        assertEquals(first, MaterialContainerImage.fingerprint("machine", "container:test", layout, reordered));
        assertNotEquals(first, MaterialContainerImage.fingerprint("machine", "container:test", layout, fuelled));
    }
    @Test void incompleteOverlappingAndUnknownCompartmentDeclarationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ContainerCompartmentLayout(3,
                Map.of(ContainerSectionId.INPUT, Set.of(0), ContainerSectionId.FUEL, Set.of(2))));
        assertThrows(IllegalArgumentException.class, () -> new ContainerCompartmentLayout(2,
                Map.of(ContainerSectionId.INPUT, Set.of(0, 1), ContainerSectionId.FUEL, Set.of(1))));
        assertThrows(IllegalArgumentException.class, () -> ContainerCompartmentLayout.stock(27).slots(ContainerSectionId.FUEL));
    }
}
