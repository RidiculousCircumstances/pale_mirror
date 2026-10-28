package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FungibleCustodyReadTest {
    private static final SubjectId OWNER = new SubjectId("settlement:owner"), ACTOR = new SubjectId("resident:holder");
    private static final SubjectId FIRST = new SubjectId("custody:a"), SECOND = new SubjectId("custody:b");
    private static final SubjectId LOT_A = new SubjectId("lot:a"), LOT_B = new SubjectId("lot:b");
    private static final ResourceCustody.Actor HOLDER = new ResourceCustody.Actor(ACTOR);
    private static FungibleResourceLedger ledger(int firstQuantity, boolean reverse) {
        var first = new CustodyAccount(FIRST, HOLDER, Map.of(LOT_A, firstQuantity), Map.of());
        var second = new CustodyAccount(SECOND, HOLDER, Map.of(LOT_B, 3), Map.of());
        var accounts = new LinkedHashMap<SubjectId, CustodyAccount>();
        if (reverse) { accounts.put(SECOND, second); accounts.put(FIRST, first); }
        else { accounts.put(FIRST, first); accounts.put(SECOND, second); }
        return new FungibleResourceLedger(Map.of(LOT_A, new ResourceLot(LOT_A, OWNER, "minecraft:bread", firstQuantity, "fixture:read", List.of()),
                LOT_B, new ResourceLot(LOT_B, OWNER, "minecraft:bread", 3, "fixture:read", List.of())), Map.of(), accounts, Map.of());
    }
    @Test void unchangedMembershipStillReadsCurrentQuantitiesAndKeepsExactRegistryOrder() {
        var original = ledger(4, true);
        var held = FungibleResourceReadIndex.actorAccounts(original, ACTOR);
        assertEquals(List.of(FIRST, SECOND), held.stream().map(CustodyAccount::id).toList());
        assertEquals(List.of(SECOND, FIRST), FungibleResourceReadIndex.accountsAt(original, HOLDER).stream().map(CustodyAccount::id).toList());
        var quantityChanged = ledger(2, true);
        assertEquals(2, FungibleResourceReadIndex.actorAccounts(quantityChanged, ACTOR).getFirst().lotQuantities().get(LOT_A));
        assertEquals(4, held.getFirst().lotQuantities().get(LOT_A), "a shared directory cannot supply predecessor quantities");
        var order = new LinkedHashMap<SubjectId, CustodyAccount>();
        order.put(FIRST, original.accounts().get(FIRST)); order.put(SECOND, original.accounts().get(SECOND));
        var reordered = new FungibleResourceLedger(original.lots(), original.claims(), order, original.bindings());
        assertEquals(List.of(FIRST, SECOND), FungibleResourceReadIndex.accountsAt(reordered, HOLDER).stream().map(CustodyAccount::id).toList());
        assertEquals(List.of(SECOND, FIRST), FungibleResourceReadIndex.accountsAt(original, HOLDER).stream().map(CustodyAccount::id).toList(),
                "equal custody membership cannot hide a changed registry iteration order");
        var source = original.accounts().get(FIRST); var pocket = new ActorItemSlot.Pocket(3);
        var presented = new CustodyAccount(FIRST, HOLDER, source.lotQuantities(), source.claimQuantities(), Optional.of(pocket));
        var current = new FungibleResourceLedger(original.lots(), original.claims(),
                ImmutableLookupMap.withEntry(original.accounts(), FIRST, presented), original.bindings());
        assertEquals(Optional.of(pocket), FungibleResourceReadIndex.actorAccounts(current, ACTOR).getFirst().actorPresentation());
        assertTrue(held.getFirst().actorPresentation().isEmpty(), "membership reuse cannot retain an old inventory presentation");
    }
    @Test void warmedAvailabilityReadsCannotLendTitleOrClaimsToAnotherImmutableImage() throws Exception {
        var original = ledger(4, false);
        assertEquals(4, original.unclaimedQuantity(FIRST, OWNER, "minecraft:bread"));
        assertEquals(4, original.unclaimedQuantity(FIRST, OWNER, "minecraft:bread"));
        var other = new SubjectId("settlement:other");
        var first = original.lots().get(LOT_A);
        var titled = new FungibleResourceLedger(ImmutableLookupMap.withEntry(original.lots(), LOT_A,
                first.withEconomicOwner(other)),
                original.claims(), original.accounts(), original.bindings());
        assertEquals(0, titled.unclaimedQuantity(FIRST, OWNER, "minecraft:bread"));
        assertEquals(4, titled.unclaimedQuantity(FIRST, other, "minecraft:bread"));
        var reserved = original.reserve(new ClaimAllocation(new SubjectId("claim:availability"), new SubjectId("process:meal"),
                OWNER, "minecraft:bread", 2, Map.of(), ClaimPurpose.EXTERNAL_RESERVATION), FIRST);
        var task = new java.util.concurrent.FutureTask<Integer>(() -> reserved.unclaimedQuantity(FIRST, OWNER, "minecraft:bread"));
        new Thread(task).start(); assertEquals(2, task.get());
        assertEquals(2, reserved.unclaimedQuantity(FIRST, OWNER, "minecraft:bread"));
        assertTrue(FungibleResourceReadIndex.carriesClaim(reserved, HOLDER), "one holder may retain several current accounts");
        assertFalse(FungibleResourceReadIndex.carriesClaim(original, HOLDER), "placement reuse is not a retained claim");
        assertTrue(FungibleResourceReadIndex.carriesClaim(reserved, HOLDER));
        assertEquals(4, original.unclaimedQuantity(FIRST, OWNER, "minecraft:bread"));
        assertEquals(0, titled.unclaimedQuantity(FIRST, OWNER, "minecraft:bread"));
    }
    @Test void transfersAndClaimReleaseRebuildOnlyReadFactsWithoutLosingTheCurrentHolder() {
        var actors = ledger(4, false); var container = new SubjectId("container:source");
        var source = new CustodyAccount(FIRST, new ResourceCustody.Container(container), Map.of(LOT_A, 4), Map.of());
        var original = new FungibleResourceLedger(actors.lots(), actors.claims(), Map.of(FIRST, source,
                SECOND, actors.accounts().get(SECOND)), actors.bindings());
        var ground = new ResourceCustody.Ground(new UUID(1, 2), new BlockPosition(4, 64, 7));
        var account = new CustodyAccount(new SubjectId("custody:ground"), ground, Map.of(LOT_A, 2), Map.of());
        assertTrue(FungibleResourceReadIndex.groundAccounts(original).isEmpty());
        var moved = original.transferToNewAccount(FIRST, account);
        assertEquals(List.of(account), FungibleResourceReadIndex.groundAccounts(moved));
        assertEquals(2, FungibleResourceReadIndex.container(moved, container).orElseThrow().lotQuantities().get(LOT_A));
        assertEquals(4, FungibleResourceReadIndex.container(original, container).orElseThrow().lotQuantities().get(LOT_A));
        var claim = new SubjectId("claim:ground");
        var reserved = moved.reserve(new ClaimAllocation(claim, new SubjectId("process:pickup"), OWNER, "minecraft:bread", 1,
                Map.of(), ClaimPurpose.EXTERNAL_RESERVATION), account.id());
        assertTrue(FungibleResourceReadIndex.carriesClaim(reserved, ground));
        assertFalse(FungibleResourceReadIndex.carriesClaim(moved, ground));
        assertFalse(FungibleResourceReadIndex.carriesClaim(reserved.releaseClaim(account.id(), claim), ground));
        assertTrue(FungibleResourceReadIndex.groundAccounts(original).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> FungibleResourceReadIndex.actorAccounts(moved, ACTOR).clear());
    }
    @Test void registryOrderViewChecksUnknownMutableInputsAndKeepsFrozenPredecessors() {
        var source = new LinkedHashMap<String, Integer>(); source.put("b", 1); source.put("a", 2);
        var view = new ImmutableKeyOrderView<String>(2); var before = view.get(source);
        source.clear(); source.put("a", 3); source.put("b", 4);
        assertEquals(List.of("a", "b"), view.get(source)); assertEquals(List.of("b", "a"), before);
        assertThrows(UnsupportedOperationException.class, () -> before.clear());
        source.put("c", 5); assertThrows(IllegalArgumentException.class, () -> view.get(source));
    }
}
