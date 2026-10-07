package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Objects;

/** Personal resource custody survives an activity switch. Job claims and physical slots stay separate. */
public final class ActorCarriedResources {
    public static final int MAX_STACK_ACCOUNTS = 10;
    private ActorCarriedResources() { }

    /** Quantity in one exact homogeneous stack account, not in another actor or a work-area counter. */
    public static int stackQuantity(FungibleResourceLedger ledger, SubjectId actorId, SubjectId accountId,
                                     SubjectId economicOwnerId, String itemKind) {
        Objects.requireNonNull(ledger); Objects.requireNonNull(actorId); Objects.requireNonNull(accountId);
        Objects.requireNonNull(economicOwnerId); Objects.requireNonNull(itemKind);
        CustodyAccount account = ledger.accounts().get(accountId);
        if (account == null) return 0;
        if (!account.custody().equals(new ResourceCustody.Actor(actorId)))
            throw new IllegalArgumentException("stack quantity has a foreign physical custodian");
        int quantity = 0;
        for (var entry : account.lotQuantities().entrySet()) {
            ResourceLot lot = ledger.lots().get(entry.getKey());
            if (lot == null || !lot.economicOwnerId().equals(economicOwnerId) || !lot.itemKind().equals(itemKind))
                throw new IllegalArgumentException("stack quantity has foreign resource identity or ownership");
            quantity = Math.addExact(quantity, entry.getValue());
        }
        if (quantity > 64) throw new IllegalArgumentException("stack account exceeds bounded carrying capacity");
        return quantity;
    }

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
        var occupied = new java.util.HashMap<SubjectId, java.util.Set<ActorItemSlot>>();
        for (CustodyAccount account : accounts) {
            if (account.custody() instanceof ResourceCustody.Actor actor
                    && counts.merge(actor.actorId(), 1, Integer::sum) > MAX_STACK_ACCOUNTS)
                throw new IllegalArgumentException("actor carry-account capacity exceeded");
            if (account.custody() instanceof ResourceCustody.Actor actor && account.actorPresentation().isPresent()
                    && !occupied.computeIfAbsent(actor.actorId(), ignored -> new java.util.HashSet<>())
                        .add(account.actorPresentation().orElseThrow()))
                throw new IllegalArgumentException("two personal accounts claim the same inventory presentation");
        }
    }
}
