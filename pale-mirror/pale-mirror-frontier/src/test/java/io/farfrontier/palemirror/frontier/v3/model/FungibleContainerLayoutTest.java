package io.farfrontier.palemirror.frontier.v3.model;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FungibleContainerLayoutTest {
    @Test void withdrawalDoesNotShiftUnchangedStocksAndNewArrivalsUseFreeSpace() {
        var layout = new FungibleContainerLayout(Map.of(0, new FungibleContainerLayout.Stack("minecraft:cobblestone", 64),
                3, new FungibleContainerLayout.Stack("minecraft:wheat", 60),
                7, new FungibleContainerLayout.Stack("minecraft:bread", 32)));
        var withoutStone = layout.project(Map.of("minecraft:wheat", 60, "minecraft:bread", 32), 54, Set.of(20));
        assertEquals(Map.of(3, layout.slots().get(3), 7, layout.slots().get(7)), withoutStone);
        var incoming = layout.project(Map.of("minecraft:wheat", 62, "minecraft:bread", 32, "minecraft:stone", 3), 54, Set.of(20));
        assertEquals(new FungibleContainerLayout.Stack("minecraft:wheat", 62), incoming.get(3));
        assertEquals(layout.slots().get(7), incoming.get(7));
        assertEquals(new FungibleContainerLayout.Stack("minecraft:stone", 3), incoming.get(0));
        assertFalse(incoming.containsKey(20));
    }
    @Test void historicalLayoutCannotCreateStockOrAuthorizeOverCapacityProjection() {
        var layout = new FungibleContainerLayout(Map.of(7, new FungibleContainerLayout.Stack("minecraft:bread", 64)));
        assertTrue(layout.project(Map.of(), 27, Set.of()).isEmpty());
        assertEquals(Map.of(7, new FungibleContainerLayout.Stack("minecraft:bread", 1)),
                layout.project(Map.of("minecraft:bread", 1), 27, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> layout.project(Map.of("minecraft:bread", 65), 1, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new ContainerRecord(
                new io.farfrontier.palemirror.frontier.v3.api.SubjectId("container:small"),
                new io.farfrontier.palemirror.frontier.v3.api.SubjectId("settlement:1"), 2,
                ContainerPurpose.UNSCOPED, Optional.empty(), Optional.empty(), layout));
    }
}
