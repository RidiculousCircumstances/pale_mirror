package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalStackBindingIdentityTest {
    @Test void maximumAccountIdentityBindsAndTransfersWithoutExpandingTheIdentifierLimit() {
        var account = new SubjectId("custody:" + "a".repeat(128));
        var destination = new SubjectId("custody:" + "a".repeat(127) + "b");
        var owner = new SubjectId("settlement:12");
        var actor = new SubjectId("resident:12-12");
        var container = new SubjectId("container:12-depot");
        var lot = new ResourceLot(new SubjectId("lot:long-account"), owner, "minecraft:bread", 64, "test", List.of());
        var ledger = FungibleResourceLedger.empty().issue(lot,
                new CustodyAccount(account, new ResourceCustody.Container(container), Map.of(lot.id(), 64), Map.of()));
        var sourceAddress = new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(container, 0));
        var stacks = List.of(new FungiblePhysicalObservation.Stack(sourceAddress, "minecraft:bread", 64));
        var observed = FungiblePhysicalObservation.bind(ledger, account, 1L, stacks);
        assertEquals(observed, FungiblePhysicalObservation.bind(ledger, account, 1L, stacks));
        var body = java.util.UUID.fromString("00000000-0000-0000-0000-000000000012");
        var carried = FungibleResourceLedger.empty().issue(lot,
                new CustodyAccount(account, new ResourceCustody.Actor(actor), Map.of(lot.id(), 64), Map.of()));
        assertEquals(64, FungiblePhysicalObservation.bind(carried, account, 1L, List.of(
                new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(actor, body),
                        "minecraft:bread", 64))).getFirst().quantity());
        ledger = ledger.rebind(account, 1L, observed);
        var handoff = FungiblePhysicalHandoff.depart(ledger, account, 1L, observed.getFirst(), 0,
                new CustodyAccount(destination, new ResourceCustody.Player(body), Map.of(lot.id(), 64), Map.of()), 2L,
                new PhysicalStackAddress.PlayerSlot(body, 0));
        var transferred = ledger.transferObservedToNewAccount(account, handoff.destinationAccount(), 1L, 2L,
                handoff.lotQuantities(), handoff.claimQuantities(), handoff.remainingSource(), handoff.destinationBindings());
        assertEquals(64, transferred.totalQuantity(owner, "minecraft:bread"));
        assertNotEquals(observed.getFirst().id(), handoff.destinationBindings().getFirst().id());
        assertNotEquals(PhysicalStackBinding.generatedId(account, 1L, 0), PhysicalStackBinding.generatedId(account, 1L, 1));
        assertNotEquals(PhysicalStackBinding.generatedId(account, 1L, 0), PhysicalStackBinding.generatedId(account, 2L, 0));
        assertNotEquals(PhysicalStackBinding.generatedId(account, Long.MAX_VALUE, Integer.MAX_VALUE),
                PhysicalStackBinding.generatedId(destination, Long.MAX_VALUE, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new SubjectId("custody:" + "a".repeat(129)));
    }
}
