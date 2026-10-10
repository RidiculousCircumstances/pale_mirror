package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContainerStockIndexTest {
    @Test void stockChangesAndExactSlotChangesInvalidateWithoutASecondLedgerOrHistoricalCache() {
        var container = new SubjectId("container:index"); var owner = new SubjectId("settlement:index");
        var item = new SubjectId("item:tool"); var lot = new SubjectId("lot:wheat"); var account = new SubjectId("custody:index");
        var inventory = new ExactInventory(Map.of(container, new ContainerRecord(container, owner, 27)),
                Map.of(item, new ExactItemStack(item, owner, "minecraft:iron_pickaxe", 1, new InventoryCustody.ContainerSlot(container, 26))),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(container,
                    new ContainerSurface(container, new BlockPosition(0, 64, 0), ContainerSurfaceStatus.UNMATERIALIZED)));
        var baseline = ContainerStockIndex.budget(inventory, container);
        for (int i = 0; i < 64; i++) assertSame(baseline, ContainerStockIndex.budget(inventory, container));
        var ledger = new FungibleResourceLedger(Map.of(lot, new ResourceLot(lot, owner, "minecraft:wheat", 65, "test", List.of())),
                Map.of(), Map.of(account, new CustodyAccount(account, new ResourceCustody.Container(container), Map.of(lot, 65), Map.of())), Map.of());
        var stocked = inventory.withFungibleResources(ledger);
        var budget = ContainerStockIndex.budget(stocked, container);
        assertEquals(2, budget.packedStacks()); assertEquals(Map.of("minecraft:wheat", 65L), budget.stockByKind());
        assertEquals(Set.of(26), budget.occupied());
        var removed = stocked.withoutItem(item);
        assertTrue(ContainerStockIndex.budget(removed, container).occupied().isEmpty());
        assertEquals(2, ContainerStockIndex.budget(removed, container).packedStacks());
        assertEquals(baseline, ContainerStockIndex.budget(inventory, container), "older immutable states remain correct after another world/input");
    }
}
