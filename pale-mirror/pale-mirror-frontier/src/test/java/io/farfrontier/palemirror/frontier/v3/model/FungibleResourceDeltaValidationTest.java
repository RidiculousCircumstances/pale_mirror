package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

/** The independent full invariant is the oracle for changed-scope closure. */
class FungibleResourceDeltaValidationTest {
    private static final SubjectId OWNER = id("owner");
    private static final SubjectId LOT = id("lot");
    private static final SubjectId CLAIM = id("claim");
    private static final SubjectId A = id("account-a");
    private static final SubjectId B = id("account-b");
    private static final SubjectId ACTOR = id("actor");
    private static final SubjectId CHEST = id("chest");
    private static final SubjectId BINDING = id("binding");
    private static final String BREAD = "minecraft:bread";

    @Test void changedOrDeletedDeclarationsRecheckUnchangedHolders() {
        var before = fixture();
        rejects(before, d -> d.lots.remove(LOT));
        rejects(before, d -> d.lots.put(LOT, d.lots.get(LOT).withQuantity(13)));
        rejects(before, d -> d.lots.put(LOT, new ResourceLot(LOT, id("foreign"), BREAD, 12, "fixture", List.of())));
        rejects(before, d -> d.claims.remove(CLAIM));
        rejects(before, d -> d.claims.put(CLAIM, claim(OWNER, BREAD, 4, Map.of(LOT, 4))));
        rejects(before, d -> d.claims.put(CLAIM, claim(id("foreign"), BREAD, 3, Map.of(LOT, 3))));
        rejects(before, d -> d.claims.put(CLAIM, claim(OWNER, "minecraft:wheat", 3, Map.of(LOT, 3))));
        rejects(before, d -> d.claims.put(CLAIM, claim(OWNER, BREAD, 3, Map.of(id("missing"), 3))));
    }

    @Test void accountRemovalAndNewReferencesCannotLoseOrMintCustody() {
        var before = fixture();
        rejects(before, d -> d.accounts.remove(B));
        rejects(before, d -> d.accounts.put(B, account(B, 6)));
        rejects(before, d -> d.accounts.put(B, new CustodyAccount(B, new ResourceCustody.Container(id("other")),
                Map.of(id("unknown-lot"), 7), Map.of())));
        rejects(before, d -> d.accounts.put(B, new CustodyAccount(B, new ResourceCustody.Container(id("other")),
                Map.of(LOT, 7), Map.of(id("unknown-claim"), 1))));
    }

    @Test void bindingClosureIncludesOldAccountsUnchangedBindingsAndAllAddresses() {
        var before = fixture();
        // All declared quantities survive the move, but the old binding loses its account.
        rejects(before, d -> {
            d.accounts.remove(A);
            d.accounts.put(B, new CustodyAccount(B, new ResourceCustody.Container(id("other")),
                    Map.of(LOT, 12), Map.of(CLAIM, 3)));
        });
        // Kind metadata changes are consistent in custody but not in an unchanged HOT stack.
        rejects(before, d -> {
            d.lots.put(LOT, new ResourceLot(LOT, OWNER, "minecraft:wheat", 12, "fixture", List.of()));
            d.claims.put(CLAIM, claim(OWNER, "minecraft:wheat", 3, Map.of(LOT, 3)));
        });
        rejects(before, d -> d.bindings.put(BINDING, binding(BINDING, A, 4, Map.of(CLAIM, 3))));
        // Each binding completely covers its own account. Only global address uniqueness rejects this.
        rejects(before, d -> {
            d.accounts.put(B, new CustodyAccount(B, new ResourceCustody.Container(CHEST), Map.of(LOT, 7), Map.of()));
            d.bindings.put(id("second-binding"), binding(id("second-binding"), B, 7, Map.of()));
        });
        rejects(before, d -> d.bindings.put(BINDING, binding(BINDING, B, 7, Map.of())));
    }

    @Test void actorCapacityAndPresentationIncludeUnchangedPeerAccounts() {
        var lots = new HashMap<SubjectId, ResourceLot>();
        var accounts = new HashMap<SubjectId, CustodyAccount>();
        for (int i = 0; i < 11; i++) {
            var lot = id("actor-lot-" + i); var account = id("actor-account-" + i);
            lots.put(lot, new ResourceLot(lot, OWNER, BREAD, 1, "fixture", List.of()));
            ResourceCustody custody = i == 10 ? new ResourceCustody.Container(CHEST) : new ResourceCustody.Actor(ACTOR);
            accounts.put(account, new CustodyAccount(account, custody, Map.of(lot, 1), Map.of(),
                    i == 0 ? Optional.of(new ActorItemSlot.Pocket(0)) : Optional.empty()));
        }
        var before = new FungibleResourceLedger(lots, Map.of(), accounts, Map.of());
        rejects(before, d -> {
            var old = accounts.get(id("actor-account-10"));
            d.accounts.put(old.id(), new CustodyAccount(old.id(), new ResourceCustody.Actor(ACTOR),
                    old.lotQuantities(), Map.of()));
        });
        rejects(before, d -> {
            var old = accounts.get(id("actor-account-1"));
            d.accounts.put(old.id(), new CustodyAccount(old.id(), old.custody(), old.lotQuantities(), Map.of(),
                    Optional.of(new ActorItemSlot.Pocket(0))));
        });
    }

    @Test void invalidMapKeysCannotEnterAnyOfTheFourComponents() {
        var before = fixture();
        rejects(before, d -> d.lots.put(id("wrong-key"), d.lots.get(LOT)));
        rejects(before, d -> d.claims.put(id("wrong-key"), d.claims.get(CLAIM)));
        rejects(before, d -> d.accounts.put(id("wrong-key"), d.accounts.get(A)));
        rejects(before, d -> d.bindings.put(id("wrong-key"), d.bindings.get(BINDING)));
    }

    @Test void validBranchesAfterFailedCandidatesPreserveFullAuditSemantics() {
        var before = fixture();
        rejects(before, d -> d.bindings.put(id("duplicate"), binding(id("duplicate"), A, 5, Map.of(CLAIM, 3))));
        var d = new Draft(before);
        d.bindings.clear();
        d.accounts.put(A, new CustodyAccount(A, new ResourceCustody.Container(CHEST), Map.of(LOT, 4), Map.of(CLAIM, 3)));
        d.accounts.put(B, account(B, 8));
        d.accept(before);
        // Intrinsic validation does not add the read-index's stronger section-uniqueness rule.
        d.accounts.put(B, new CustodyAccount(B, new ResourceCustody.Container(CHEST), Map.of(LOT, 8), Map.of()));
        d.accept(before);
        ImmutableInvariantCheck.full(() -> { d.accept(before); return null; });
        ImmutableInvariantCheck.full(() -> {
            rejects(before, bad -> bad.lots.remove(LOT)); return null;
        });
        before.validateComplete();
    }

    @Test void realSuccessorsRetainClaimsAndPhysicalCoverageAcrossTransferReleaseAndDestruction() {
        var before = fixture();
        var cold = before.releaseBindings(A, 1);
        var next = cold.transfer(A, B, Map.of(LOT, 1), Map.of());
        next = next.releaseClaim(A, CLAIM);
        next = next.destroy(B, Map.of(LOT, 2), Map.of());
        assertEquals(10, next.lots().get(LOT).quantity());
        assertEquals(Map.of(LOT, 4), next.accounts().get(A).lotQuantities());
        next.validateComplete(); before.validateComplete();
        assertEquals(12, before.lots().get(LOT).quantity());
    }

    private static FungibleResourceLedger fixture() {
        var lot = new ResourceLot(LOT, OWNER, BREAD, 12, "fixture", List.of());
        var account = new CustodyAccount(A, new ResourceCustody.Container(CHEST), Map.of(LOT, 5), Map.of(CLAIM, 3));
        return new FungibleResourceLedger(Map.of(LOT, lot), Map.of(CLAIM, claim(OWNER, BREAD, 3, Map.of(LOT, 3))),
                Map.of(A, account, B, account(B, 7)), Map.of(BINDING, binding(BINDING, A, 5, Map.of(CLAIM, 3))));
    }
    private static CustodyAccount account(SubjectId id, int quantity) {
        return new CustodyAccount(id, new ResourceCustody.Container(id("other")), Map.of(LOT, quantity), Map.of());
    }
    private static ClaimAllocation claim(SubjectId owner, String kind, int quantity, Map<SubjectId, Integer> pinned) {
        return new ClaimAllocation(CLAIM, id("job"), owner, kind, quantity, pinned, ClaimPurpose.EXTERNAL_RESERVATION);
    }
    private static PhysicalStackBinding binding(SubjectId id, SubjectId account, int quantity, Map<SubjectId, Integer> claims) {
        return new PhysicalStackBinding(id, account,
                new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(CHEST, 0)),
                1, BREAD, Map.of(LOT, quantity), claims);
    }
    private static void rejects(FungibleResourceLedger before, Consumer<Draft> change) {
        var draft = new Draft(before); change.accept(draft);
        assertThrows(IllegalArgumentException.class, () -> draft.full());
        assertThrows(IllegalArgumentException.class, () -> draft.delta(before));
        before.validateComplete();
        new Draft(before).accept(before); // Revisit an earlier branch after a failed candidate.
    }
    private static SubjectId id(String value) { return new SubjectId("delta:" + value); }
    private static final class Draft {
        final Map<SubjectId, ResourceLot> lots;
        final Map<SubjectId, ClaimAllocation> claims;
        final Map<SubjectId, CustodyAccount> accounts;
        final Map<SubjectId, PhysicalStackBinding> bindings;
        Draft(FungibleResourceLedger before) {
            lots = new HashMap<>(before.lots()); claims = new HashMap<>(before.claims());
            accounts = new HashMap<>(before.accounts()); bindings = new HashMap<>(before.bindings());
        }
        void full() { new FungibleResourceLedger(lots, claims, accounts, bindings); }
        void delta(FungibleResourceLedger before) {
            FungibleResourceDeltaValidation.validate(before, ImmutableLookupMap.copyOf(lots), ImmutableLookupMap.copyOf(claims),
                    ImmutableLookupMap.copyOf(accounts), ImmutableLookupMap.copyOf(bindings));
        }
        void accept(FungibleResourceLedger before) { full(); delta(before); }
    }
}
