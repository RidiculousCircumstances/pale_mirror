package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import java.util.Map;
import java.util.Objects;

/** Checked increments over a valid ledger: fresh issued lot/account or one existing
 * unbound COLD part. Equal lot/account deltas preserve global conservation; claims
 * and physical bindings remain unchanged. Full ingress/recovery stays separate. */
final class FungibleResourceAccrual {
    /** Only the checked factory can construct this proof; its predecessor cannot be replaced. */
    static final class Prepared {
        private final FungibleResourceLedger before;
        private final Map<SubjectId, ResourceLot> lots;
        private final Map<SubjectId, CustodyAccount> accounts;
        private Prepared(FungibleResourceLedger before, Map<SubjectId, ResourceLot> lots,
                         Map<SubjectId, CustodyAccount> accounts) {
            this.before = before; this.lots = lots; this.accounts = accounts;
        }
        FungibleResourceLedger before() { return before; }
        Map<SubjectId, ResourceLot> lots() { return lots; }
        Map<SubjectId, CustodyAccount> accounts() { return accounts; }
    }
    static Prepared issue(FungibleResourceLedger before, ResourceLot lot, CustodyAccount account) {
        Objects.requireNonNull(before); Objects.requireNonNull(lot, "issued lot"); Objects.requireNonNull(account, "issued account");
        if (before.lots().containsKey(lot.id()) || before.accounts().containsKey(account.id())
                || !account.lotQuantities().equals(Map.of(lot.id(), lot.quantity())) || !account.claimQuantities().isEmpty())
            throw new IllegalArgumentException("fungible resource issue does not establish one fresh exact account");
        if (before.lots().size() == FungibleResourceLedger.MAX_LOTS || before.accounts().size() == FungibleResourceLedger.MAX_ACCOUNTS)
            throw new IllegalArgumentException("fungible resource retention limit exceeded");
        if (account.custody() instanceof ResourceCustody.Actor actor) {
            var carried = new java.util.ArrayList<CustodyAccount>();
            for (var existing : before.accounts().values())
                if (existing.custody() instanceof ResourceCustody.Actor owner && owner.actorId().equals(actor.actorId())) carried.add(existing);
            carried.add(account); ActorCarriedResources.validateCapacity(carried);
        }
        var lots = ImmutableLookupMap.withEntry(before.lots(), lot.id(), lot);
        var accounts = ImmutableLookupMap.withEntry(before.accounts(), account.id(), account);
        FungibleResourceValidation.validateAccount(lots, before.claims(), account);
        return new Prepared(before, lots, accounts);
    }
    static Prepared prepare(FungibleResourceLedger before, BlockExtraction.Output extracted,
                            ResourceLot next, SubjectId accountId, SubjectId actorId) {
        Objects.requireNonNull(before); Objects.requireNonNull(extracted); Objects.requireNonNull(next);
        Objects.requireNonNull(accountId); Objects.requireNonNull(actorId);
        if (!next.itemKind().equals(extracted.itemKind()) || next.quantity() > ActorCarriedResources.MAX_STACK_ITEMS
                || !next.lineage().isEmpty())
            throw new IllegalArgumentException("extraction part must match its bounded original output");
        var account = before.accounts().get(accountId); var prior = before.lots().get(next.id());
        if (account == null || !(account.custody() instanceof ResourceCustody.Actor actor) || !actor.actorId().equals(actorId)
                || prior == null || !account.lotQuantities().equals(Map.of(next.id(), prior.quantity()))
                || !account.claimQuantities().isEmpty()
                || before.bindings().values().stream().anyMatch(binding -> binding.accountId().equals(accountId))
                || prior.quantity() >= ActorCarriedResources.MAX_STACK_ITEMS
                || next.quantity() != Math.addExact(prior.quantity(), extracted.quantity())
                || !next.withQuantity(prior.quantity()).equals(prior))
            throw new IllegalArgumentException("actor extraction accrual lacks its exact unbound predecessor part");
        var changed = account.withQuantities(Map.of(next.id(), next.quantity()), Map.of());
        var lots = ImmutableLookupMap.withEntry(before.lots(), next.id(), next);
        var accounts = ImmutableLookupMap.withEntry(before.accounts(), accountId, changed);
        FungibleResourceValidation.validateAccount(lots, before.claims(), changed);
        return new Prepared(before, lots, accounts);
    }
    private FungibleResourceAccrual() { }
}
