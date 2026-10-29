package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
import java.util.function.Function;

/** Intrinsic resource invariants closed over a valid immutable predecessor.
 * No allocation, custody authority or successful-validation cache lives here.
 * External ingress and explicit audits always use the independent full validator. */
final class FungibleResourceDeltaValidation {
    private static final ImmutableGroupView<SubjectId, PhysicalStackBinding, SubjectId> BINDINGS = new ImmutableGroupView<>();
    private static final ImmutableGroupView<SubjectId, PhysicalStackBinding, PhysicalStackAddress> ADDRESSES = new ImmutableGroupView<>();
    private FungibleResourceDeltaValidation() { }

    /** Candidate maps have already been defensively captured by the private ledger constructor. */
    static void validate(FungibleResourceLedger before, Map<SubjectId, ResourceLot> lots,
                         Map<SubjectId, ClaimAllocation> claims, Map<SubjectId, CustodyAccount> accounts,
                         Map<SubjectId, PhysicalStackBinding> bindings) {
        if (ImmutableInvariantCheck.fullAudit()) {
            FungibleResourceValidation.validateComplete(lots, claims, accounts, bindings); return;
        }
        FungibleResourceValidation.requireBounds(lots, claims, accounts, bindings);
        var lotIds = changed(before.lots(), lots, ResourceLot::id, "resource lot");
        var claimIds = changed(before.claims(), claims, ClaimAllocation::id, "claim allocation");
        var accountIds = changed(before.accounts(), accounts, CustodyAccount::id, "custody account");
        var bindingIds = changed(before.bindings(), bindings, PhysicalStackBinding::id, "physical stack binding");
        var actorIds = new HashSet<SubjectId>();
        for (var id : accountIds) {
            include(before.accounts().get(id), lotIds, claimIds, actorIds);
            include(accounts.get(id), lotIds, claimIds, actorIds);
        }
        for (var id : bindingIds) {
            var old = before.bindings().get(id); var next = bindings.get(id);
            if (old != null) accountIds.add(old.accountId());
            if (next != null) accountIds.add(next.accountId());
        }
        // Metadata changes must recheck unchanged holders as well. Removed
        // declarations still require a zero total, rather than disappearing
        // from the conservation proof. No cached balance is authoritative.
        for (var id : lotIds) {
            var holders = FungibleAccountReferences.lotHolders(accounts, id); int total = 0;
            for (var account : holders) {
                total = Math.addExact(total, account.lotQuantities().get(id)); accountIds.add(account.id());
            }
            var lot = lots.get(id);
            if (total != (lot == null ? 0 : lot.quantity()))
                throw new IllegalArgumentException("resource lot must have exact canonical custody");
        }
        for (var id : claimIds) {
            var holders = FungibleAccountReferences.claimHolders(accounts, id); int total = 0;
            for (var account : holders) {
                total = Math.addExact(total, account.claimQuantities().get(id)); accountIds.add(account.id());
            }
            var claim = claims.get(id);
            if (total != (claim == null ? 0 : claim.quantity()))
                throw new IllegalArgumentException("claim allocation must have exact canonical custody");
        }
        for (var id : actorIds) ActorCarriedResources.validateCapacity(FungibleAccountReferences.actorAccounts(accounts, id));
        if (before.bindings() != bindings && !ADDRESSES.uniqueMemberships(
                FungibleResourceDeltaValidation.class, bindings, binding -> Set.of(binding.address())))
            throw new IllegalArgumentException("physical stack binding is not one unique current stack");
        for (var id : accountIds) {
            var account = accounts.get(id);
            if (account != null) FungibleResourceValidation.validateAccount(lots, claims, account);
            var rows = BINDINGS.unorderedRows(FungibleResourceDeltaValidation.class, bindings,
                    binding -> Set.of(binding.accountId()), id);
            if (rows.isEmpty()) continue; // Completely unbound custody is permitted by the full invariant.
            var scoped = new HashMap<SubjectId, PhysicalStackBinding>();
            for (var binding : rows) scoped.put(binding.id(), binding);
            FungibleResourceValidation.validateBindings(lots, claims,
                    account == null ? Map.of() : Map.of(id, account), scoped);
        }
    }

    private static <T> Set<SubjectId> changed(Map<SubjectId, T> before, Map<SubjectId, T> after,
                                            Function<T, SubjectId> identity, String label) {
        var ids = new HashSet<SubjectId>();
        ImmutableMapChanges.forEachIdentityChangedKey(before, after, key -> {
            var row = after.get(key);
            if (row != null && !key.equals(identity.apply(row)))
                throw new IllegalArgumentException(label + " map key does not match identity");
            ids.add(key);
        });
        return ids;
    }
    private static void include(CustodyAccount account, Set<SubjectId> lots, Set<SubjectId> claims,
                                Set<SubjectId> actors) {
        if (account == null) return;
        lots.addAll(account.lotQuantities().keySet()); claims.addAll(account.claimQuantities().keySet());
        if (account.custody() instanceof ResourceCustody.Actor actor) actors.add(actor.actorId());
    }
}
