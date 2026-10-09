package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Objects;

/** Personal resource custody survives an activity switch. Job claims and physical slots stay separate. */
public final class ActorCarriedResources {
    /** One physical stack is a carrying boundary, never a work-completion criterion. */
    public static final int MAX_STACK_ITEMS = 64;
    public static final int MAX_STACK_ACCOUNTS = 10;
    private record AccountIndex(java.util.Map<SubjectId, CustodyAccount> source,
                                java.util.Map<SubjectId, List<CustodyAccount>> actors) { }
    // One immutable-account image per thread, not retained world history. No identity hashing
    // of the whole ledger and no full-account scan for every resident's inventory query.
    private static final ThreadLocal<AccountIndex> ACCOUNT_INDEX = new ThreadLocal<>();
    private ActorCarriedResources() { }

    /** The resource ledger, not the activity scene or body fence, owns this binding's epoch. */
    public static PhysicalStackBinding requireBinding(FungibleResourceLedger ledger, SubjectId actorId,
                                                       SubjectId accountId, FungiblePhysicalObservation.Stack observed) {
        Objects.requireNonNull(ledger); Objects.requireNonNull(actorId); Objects.requireNonNull(accountId);
        Objects.requireNonNull(observed);
        CustodyAccount account = ledger.accounts().get(accountId);
        var bindings = ledger.bindings().values().stream().filter(value -> value.accountId().equals(accountId)).toList();
        if (!(observed.address() instanceof PhysicalStackAddress.ActorStack stack) || !stack.actorId().equals(actorId)
                || account == null || !account.custody().equals(new ResourceCustody.Actor(actorId))
                || bindings.size() != 1)
            throw new IllegalArgumentException("carried stack lacks its exact actor account and sole binding");
        var binding = bindings.getFirst();
        if (!binding.address().equals(observed.address()) || !binding.itemKind().equals(observed.itemKind())
                || binding.quantity() != observed.quantity()
                || !binding.lotQuantities().equals(account.lotQuantities())
                || !binding.claimQuantities().equals(account.claimQuantities()))
            throw new IllegalArgumentException("carried stack disagrees with its current resource binding");
        return binding;
    }

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
        if (quantity > MAX_STACK_ITEMS) throw new IllegalArgumentException("stack account exceeds bounded carrying capacity");
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
            if (account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum() > MAX_STACK_ITEMS
                    || account.lotQuantities().keySet().stream().map(id -> ledger.lots().get(id).itemKind())
                        .distinct().count() != 1)
                throw new IllegalArgumentException("carried presentation must represent one bounded stack");
            return account;
        }
    }

    public static List<CustodyAccount> accounts(FungibleResourceLedger ledger, SubjectId actorId) {
        Objects.requireNonNull(ledger); Objects.requireNonNull(actorId);
        var index = ACCOUNT_INDEX.get();
        if (index == null || index.source() != ledger.accounts()) {
            var grouped = new java.util.HashMap<SubjectId, java.util.ArrayList<CustodyAccount>>();
            for (var account : ledger.accounts().values())
                if (account.custody() instanceof ResourceCustody.Actor actor)
                    grouped.computeIfAbsent(actor.actorId(), ignored -> new java.util.ArrayList<>()).add(account);
            var actors = new java.util.HashMap<SubjectId, List<CustodyAccount>>();
            grouped.forEach((actor, values) -> {
                values.sort(java.util.Comparator.comparing(CustodyAccount::id));
                actors.put(actor, List.copyOf(values));
            });
            index = new AccountIndex(ledger.accounts(), java.util.Map.copyOf(actors));
            ACCOUNT_INDEX.set(index);
        }
        return index.actors().getOrDefault(actorId, List.of());
    }

    /** Shared admission query; personal stock is not itself an exclusive work claim. */
    public static boolean canAddAccount(FungibleResourceLedger ledger, SubjectId actorId, SubjectId accountId) {
        Objects.requireNonNull(ledger); Objects.requireNonNull(actorId); Objects.requireNonNull(accountId);
        return !ledger.accounts().containsKey(accountId) && accounts(ledger, actorId).size() < MAX_STACK_ACCOUNTS;
    }

    public static void requireNewAccountCapacity(FungibleResourceLedger ledger, SubjectId actorId, SubjectId accountId) {
        if (!canAddAccount(ledger, actorId, accountId))
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
