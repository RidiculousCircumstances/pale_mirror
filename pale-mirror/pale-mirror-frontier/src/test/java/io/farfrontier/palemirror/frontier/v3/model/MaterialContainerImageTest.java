package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaterialContainerImageTest {
    private static final String SCOPE = "container.settlement-depot";
    private static final String ID = "container:7-depot";

    @Test
    void bulkStockIgnoresSlotPermutationAndStackSplitButNotQuantityOrKind() {
        var initial = List.of(MaterialContainerImage.Slot.fungible(0, "minecraft:wheat", 64),
                MaterialContainerImage.Slot.fungible(1, "minecraft:bread", 20), MaterialContainerImage.Slot.empty(2));
        var permuted = List.of(MaterialContainerImage.Slot.fungible(0, "minecraft:bread", 20),
                MaterialContainerImage.Slot.empty(1), MaterialContainerImage.Slot.fungible(2, "minecraft:wheat", 64));
        String fingerprint = MaterialContainerImage.fingerprint(SCOPE, ID, MaterialContainerImage.Layout.BULK, initial);
        assertEquals(fingerprint, MaterialContainerImage.fingerprint(SCOPE, ID, MaterialContainerImage.Layout.BULK, permuted));
        assertEquals(fingerprint, MaterialContainerImage.fingerprint(SCOPE, ID, MaterialContainerImage.Layout.BULK,
                List.of(MaterialContainerImage.Slot.fungible(0, "minecraft:wheat", 32),
                        MaterialContainerImage.Slot.fungible(1, "minecraft:bread", 20),
                        MaterialContainerImage.Slot.fungible(2, "minecraft:wheat", 32))));
        assertNotEquals(fingerprint, MaterialContainerImage.fingerprint(SCOPE, ID, MaterialContainerImage.Layout.BULK,
                List.of(MaterialContainerImage.Slot.fungible(0, "minecraft:bread", 20),
                        MaterialContainerImage.Slot.empty(1), MaterialContainerImage.Slot.fungible(2, "minecraft:wheat", 63))));
    }

    @Test
    void exactItemsAndProductionPortsRemainPositionSensitive() {
        var first = List.of(MaterialContainerImage.Slot.exact(0, "item:tool", "minecraft:iron_pickaxe", 1),
                MaterialContainerImage.Slot.fungible(1, "minecraft:wheat", 1));
        var second = List.of(MaterialContainerImage.Slot.fungible(0, "minecraft:wheat", 1),
                MaterialContainerImage.Slot.exact(1, "item:tool", "minecraft:iron_pickaxe", 1));
        assertNotEquals(MaterialContainerImage.fingerprint(SCOPE, ID, MaterialContainerImage.Layout.BULK, first),
                MaterialContainerImage.fingerprint(SCOPE, ID, MaterialContainerImage.Layout.BULK, second));
        var stationBefore = List.of(MaterialContainerImage.Slot.fungible(0, "minecraft:wheat", 1),
                MaterialContainerImage.Slot.empty(1));
        var stationAfter = List.of(MaterialContainerImage.Slot.empty(0),
                MaterialContainerImage.Slot.fungible(1, "minecraft:wheat", 1));
        assertNotEquals(MaterialContainerImage.fingerprint(SCOPE, ID, MaterialContainerImage.Layout.FIXED_PORTS, stationBefore),
                MaterialContainerImage.fingerprint(SCOPE, ID, MaterialContainerImage.Layout.FIXED_PORTS, stationAfter));
        assertThrows(IllegalArgumentException.class, () -> MaterialContainerImage.fingerprint(SCOPE, ID,
                MaterialContainerImage.Layout.BULK, List.of(MaterialContainerImage.Slot.empty(1))));
    }
}
