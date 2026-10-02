package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Objects;

/** Personal resource custody survives an activity switch. Job claims and physical slots stay separate. */
public final class ActorCarriedResources {
    public static final int MAX_STACK_ACCOUNTS = 10;
    private ActorCarriedResources() { }

    public record Presentation(SubjectId actorId, SubjectId accountId, ActorItemSlot slot) {
        public Presentation {
            Objects.requireNonNull(actorId, "carrying actor");
            Objects.requireNonNull(accountId, "carried account");
            Objects.requireNonNull(slot, "carried presentation slot");
        }
        public CustodyAccount requireAccount(FungibleResourceLedger ledger) {
            CustodyAccount account = ledger.accounts().get(accountId);
            if (account == null || !account.custody().equals(new ResourceCustody.Actor(actorId)))
                throw new IllegalArgumentException("carried presentation has no exact actor custody");
            if (account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum() > 64
                    || account.lotQuantities().keySet().stream().map(id -> ledger.lots().get(id).itemKind())
                        .distinct().count() != 1)
                throw new IllegalArgumentException("carried presentation must represent one bounded stack");
            return account;
        }
    }

    public static List<CustodyAccount> accounts(FungibleResourceLedger ledger, SubjectId actorId) {
        return ledger.accounts().values().stream()
                .filter(account -> account.custody().equals(new ResourceCustody.Actor(actorId)))
                .sorted(java.util.Comparator.comparing(CustodyAccount::id)).toList();
    }

    public static void requireNewAccountCapacity(FungibleResourceLedger ledger, SubjectId actorId, SubjectId accountId) {
        if (ledger.accounts().containsKey(accountId) || accounts(ledger, actorId).size() >= MAX_STACK_ACCOUNTS)
            throw new IllegalArgumentException("actor resource account exists or carry capacity is exhausted");
    }

    static void validateCapacity(java.util.Collection<CustodyAccount> accounts) {
        var counts = new java.util.HashMap<SubjectId, Integer>();
        for (CustodyAccount account : accounts) {
            if (account.custody() instanceof ResourceCustody.Actor actor
                    && counts.merge(actor.actorId(), 1, Integer::sum) > MAX_STACK_ACCOUNTS)
                throw new IllegalArgumentException("actor carry-account capacity exceeded");
        }
    }
}
