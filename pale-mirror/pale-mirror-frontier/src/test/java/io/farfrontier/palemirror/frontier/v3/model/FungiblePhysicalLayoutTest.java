package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FungiblePhysicalLayoutTest {
    @Test void retainedLotPlacementIsNotReconstructedFromAnUnchangedNativeImage() {
        var owner = new SubjectId("settlement:test");
        var container = new SubjectId("container:test");
        var account = new SubjectId("custody:test");
        var lot = new ResourceLot(new SubjectId("lot:test"), owner, "minecraft:granite", 128, "test", List.of());
        var source = new CustodyAccount(account, new ResourceCustody.Container(container), Map.of(lot.id(), 128), Map.of());
        var first = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(container, 1));
        var second = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(container, 2));
        var a = new PhysicalStackBinding(new SubjectId("binding:retained-a"), account, first, 3,
                lot.itemKind(), Map.of(lot.id(), 64), Map.of());
        var b = new PhysicalStackBinding(new SubjectId("binding:retained-b"), account, second, 3,
                lot.itemKind(), Map.of(lot.id(), 64), Map.of());
        var ledger = new FungibleResourceLedger(Map.of(lot.id(), lot), Map.of(), Map.of(account, source), Map.of(a.id(), a, b.id(), b));
        var image = List.of(new FungiblePhysicalObservation.Stack(second, lot.itemKind(), 64),
                new FungiblePhysicalObservation.Stack(first, lot.itemKind(), 64));
        assertTrue(FungiblePhysicalObservation.matchesCurrentLayout(ledger, account, 3, image));
        assertFalse(FungiblePhysicalObservation.matchesCurrentLayout(ledger, account, 4, image));
        assertFalse(FungiblePhysicalObservation.matchesCurrentLayout(ledger, account, 3, List.of(image.getFirst())));
        assertFalse(FungiblePhysicalObservation.matchesCurrentLayout(ledger, account, 3, List.of(image.getFirst(), image.getFirst())));
        assertFalse(FungiblePhysicalObservation.matchesCurrentLayout(ledger, account, 3,
                List.of(image.getFirst(), new FungiblePhysicalObservation.Stack(first, "minecraft:bread", 64))));
        assertEquals(Map.of(a.id(), a, b.id(), b), ledger.bindings());
    }
}
