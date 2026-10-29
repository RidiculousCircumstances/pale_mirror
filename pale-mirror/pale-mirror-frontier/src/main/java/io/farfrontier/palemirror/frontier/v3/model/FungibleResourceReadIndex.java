package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Disposable queries of the sole immutable ledger; never allocation or custody authority. */
final class FungibleResourceReadIndex {
    private record Balance(SubjectId owner, String kind) { }
    private record Available(CustodyAccount account, ImmutableDependencies dependencies,
                             Map<Balance, Integer> quantities, Object checkedIn) { }
    private record Image(Map<SubjectId, ResourceLot> lots, Map<SubjectId, ClaimAllocation> claims,
                         Map<SubjectId, CustodyAccount> accounts,
                         Map<SubjectId, Available> available, Object token) { }
    private sealed interface Query permits HolderQuery, ContainerQuery, ActorQuery, GroundQuery { }
    private record HolderQuery(ResourceCustody custody) implements Query { }
    private record ContainerQuery(SubjectId container) implements Query { }
    private record ActorQuery(SubjectId actor) implements Query { }
    private enum GroundQuery implements Query { INSTANCE }
    private static final ImmutableGroupView<SubjectId, CustodyAccount, Query> CUSTODIES = new ImmutableGroupView<>();
    private static final ImmutableGroupView<SubjectId, CustodyAccount, ResourceCustody.Container> SECTIONS = new ImmutableGroupView<>();
    // Retain one immutable image per owner thread, not accumulated world revisions.
    private static final ThreadLocal<Image> CURRENT = new ThreadLocal<>();
    private FungibleResourceReadIndex() { }

    static int available(FungibleResourceLedger ledger, SubjectId account, SubjectId owner, String kind) {
        Objects.requireNonNull(owner, "resource economic owner"); Objects.requireNonNull(kind, "resource item kind");
        if (!ledger.accounts().containsKey(Objects.requireNonNull(account, "custody account")))
            throw new IllegalArgumentException("unknown custody account");
        var row = ledger.accounts().get(account);
        var image = image(ledger); var views = image.available();
        var previous = views.get(account);
        // Every queried account was checked against these exact immutable roots.
        // Many residents can ask about one warehouse without rebuilding its lot
        // and claim dependency vector for each read. A new image still checks it.
        if (previous != null && previous.checkedIn() == image.token())
            return previous.quantities().getOrDefault(new Balance(owner, kind), 0);
        var inputs = new Object[1 + row.lotQuantities().size() + row.claimQuantities().size()];
        int index = 0; inputs[index++] = row;
        for (var id : row.lotQuantities().keySet()) inputs[index++] = ledger.lots().get(id);
        for (var id : row.claimQuantities().keySet()) inputs[index++] = ledger.claims().get(id);
        if (previous == null || !previous.dependencies().matches(inputs)) {
            var quantities = balances(ledger, row);
            previous = new Available(row, ImmutableDependencies.of(inputs), quantities, image.token());
        } else {
            previous = new Available(row, previous.dependencies(), previous.quantities(), image.token());
        }
        views.put(account, previous);
        return previous.quantities().getOrDefault(new Balance(owner, kind), 0);
    }

    static Optional<CustodyAccount> container(FungibleResourceLedger ledger, SubjectId container) {
        return container(ledger, container, ContainerSectionId.STOCK);
    }

    static Optional<CustodyAccount> container(FungibleResourceLedger ledger, SubjectId container, ContainerSectionId section) {
        var rows = at(ledger, new HolderQuery(new ResourceCustody.Container(Objects.requireNonNull(container), Objects.requireNonNull(section))));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    /** All retained accounts, not a first-account shortcut for physical capacity. */
    static List<CustodyAccount> containerAccounts(FungibleResourceLedger ledger, SubjectId container) {
        return sorted(at(ledger, new ContainerQuery(Objects.requireNonNull(container))));
    }

    static boolean carriesClaim(FungibleResourceLedger ledger, ResourceCustody holder) {
        return at(ledger, new HolderQuery(Objects.requireNonNull(holder))).stream().anyMatch(account -> !account.claimQuantities().isEmpty());
    }
    static List<CustodyAccount> accountsAt(FungibleResourceLedger ledger, ResourceCustody holder) {
        return at(ledger, new HolderQuery(Objects.requireNonNull(holder)), true);
    }
    static List<CustodyAccount> actorAccounts(FungibleResourceLedger ledger, SubjectId actor) {
        return sorted(at(ledger, new ActorQuery(Objects.requireNonNull(actor))));
    }
    static List<CustodyAccount> groundAccounts(FungibleResourceLedger ledger) {
        return sorted(at(ledger, GroundQuery.INSTANCE));
    }

    private static Image image(FungibleResourceLedger ledger) {
        var current = CURRENT.get();
        if (current != null && current.lots() == ledger.lots() && current.claims() == ledger.claims()
                && current.accounts() == ledger.accounts()) return current;
        // Only queried rows of the current active registry are retained. A local
        // claim change must not discard every other account's balance. Replaced or
        // withdrawn accounts (including a new world) release their old evidence.
        var available = current == null ? new HashMap<SubjectId, Available>() : current.available();
        if (current != null) ImmutableMapChanges.forEachIdentityChangedKey(
                current.accounts(), ledger.accounts(), available::remove);
        current = new Image(ledger.lots(), ledger.claims(), ledger.accounts(), available, new Object());
        CURRENT.set(current);
        return current;
    }

    /** Current rows from declared custody. Membership/value changes update only
     * affected groups; no account edit rebuilds every holder's placement directory. */
    private static List<CustodyAccount> at(FungibleResourceLedger ledger, Query query) {
        return at(ledger, query, false);
    }
    private static List<CustodyAccount> at(FungibleResourceLedger ledger, Query query, boolean producerOrder) {
        requireUniqueContainerSections(ledger);
        // A unique container account and ID-sorted aggregate reads do not need
        // ranks for every other world account. accountsAt alone promises the
        // producer's order; it must retain that explicit ordered query.
        return producerOrder
                ? CUSTODIES.rows(FungibleResourceReadIndex.class, ledger.accounts(), FungibleResourceReadIndex::memberships, query)
                : CUSTODIES.unorderedRows(FungibleResourceReadIndex.class, ledger.accounts(), FungibleResourceReadIndex::memberships, query);
    }
    static void requireUniqueContainerSections(FungibleResourceLedger ledger) {
        if (!SECTIONS.uniqueMemberships(FungibleResourceReadIndex.class, ledger.accounts(), account ->
                account.custody() instanceof ResourceCustody.Container holder ? Set.of(holder) : Set.of()))
            throw new IllegalArgumentException("container section has competing resource accounts");
    }
    private static Set<Query> memberships(CustodyAccount account) {
        var groups = new HashSet<Query>(); groups.add(new HolderQuery(account.custody()));
        if (account.custody() instanceof ResourceCustody.Container holder) groups.add(new ContainerQuery(holder.containerId()));
        else if (account.custody() instanceof ResourceCustody.Actor holder) groups.add(new ActorQuery(holder.actorId()));
        else if (account.custody() instanceof ResourceCustody.Ground) groups.add(GroundQuery.INSTANCE);
        return groups;
    }
    private static List<CustodyAccount> sorted(List<CustodyAccount> rows) {
        return rows.size() < 2 ? rows : rows.stream().sorted(Comparator.comparing(CustodyAccount::id)).toList();
    }

    private static Map<Balance, Integer> balances(FungibleResourceLedger ledger, CustodyAccount account) {
        var balances = new HashMap<Balance, Integer>();
        for (var entry : account.lotQuantities().entrySet()) {
            var lot = ledger.lots().get(entry.getKey());
            balances.merge(new Balance(lot.economicOwnerId(), lot.itemKind()), entry.getValue(), Math::addExact);
        }
        for (var entry : account.claimQuantities().entrySet()) {
            var claim = ledger.claims().get(entry.getKey());
            balances.merge(new Balance(claim.economicOwnerId(), claim.itemKind()), -entry.getValue(), Math::addExact);
        }
        return Map.copyOf(balances);
    }
}
