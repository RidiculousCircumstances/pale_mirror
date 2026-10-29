package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Disposable queries of the sole immutable ledger; never allocation or custody authority. */
final class FungibleResourceReadIndex {
    private record Balance(SubjectId owner, String kind) { }
    private record Quantities(int held, int available) {
        private static final Quantities ZERO = new Quantities(0, 0);
    }
    record Available(Map<Balance, Quantities> quantities) {
        Map<SubjectId, Map<String, Integer>> unclaimed() {
            var result = new HashMap<SubjectId, Map<String, Integer>>();
            quantities.forEach((key, value) -> result.computeIfAbsent(key.owner(), ignored -> new HashMap<>())
                    .put(key.kind(), value.available()));
            result.replaceAll((owner, values) -> Map.copyOf(values)); return Map.copyOf(result);
        }
    }
    private static final ImmutableNestedMapView<SubjectId, CustodyAccount, SubjectId, CustodyAccount> STOCK_ACCOUNTS =
            new ImmutableNestedMapView<>(FungibleResourceLedger.MAX_ACCOUNTS, FungibleResourceLedger.MAX_ACCOUNTS);
    private static final ImmutableReferenceIndex.Source<FungibleResourceLedger, SubjectId, ResourceLot> LOTS =
            new ImmutableReferenceIndex.Source<>(FungibleResourceLedger::lots);
    private static final ImmutableReferenceIndex.Source<FungibleResourceLedger, SubjectId, ClaimAllocation> CLAIMS =
            new ImmutableReferenceIndex.Source<>(FungibleResourceLedger::claims);
    private static final ImmutableReadProjection<FungibleResourceLedger, SubjectId, CustodyAccount, Available> STOCKS =
            new ImmutableReadProjection<>(FungibleResourceLedger.MAX_ACCOUNTS,
                    FungibleResourceLedger.MAX_LOTS + FungibleResourceLedger.MAX_CLAIMS, List.of(LOTS, CLAIMS),
                    (ledger, account) -> {
                        var reads = new ArrayList<ImmutableReferenceIndex.Read<FungibleResourceLedger>>();
                        account.lotQuantities().keySet().forEach(id -> reads.add(LOTS.at(id)));
                        account.claimQuantities().keySet().forEach(id -> reads.add(CLAIMS.at(id))); return reads;
                    }, (ledger, account) -> new Available(balances(ledger, account)));
    /** Container-addressed absence and amounts, shared by inventory consumers.
     * Lot title/quality and claims update only their declared account readers. */
    static Map<SubjectId, Available> stockAvailability(FungibleResourceLedger ledger) {
        requireUniqueContainerSections(ledger);
        var accounts = STOCK_ACCOUNTS.project(STOCK_ACCOUNTS, ledger.accounts(), account ->
                account.custody() instanceof ResourceCustody.Container holder && holder.section().equals(ContainerSectionId.STOCK)
                        ? Map.of(holder.containerId(), account) : Map.of());
        return STOCKS.project(STOCKS, accounts, ledger);
    }
    private record Image(Map<SubjectId, ResourceLot> lots, Map<SubjectId, ClaimAllocation> claims,
                         Map<SubjectId, CustodyAccount> accounts,
                         Map<SubjectId, Available> available) { }
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
        return quantities(ledger, account, owner, kind).available();
    }
    static int held(FungibleResourceLedger ledger, SubjectId account, SubjectId owner, String kind) {
        return quantities(ledger, account, owner, kind).held();
    }
    private static Quantities quantities(FungibleResourceLedger ledger, SubjectId account, SubjectId owner, String kind) {
        Objects.requireNonNull(owner, "resource economic owner"); Objects.requireNonNull(kind, "resource item kind");
        if (!ledger.accounts().containsKey(Objects.requireNonNull(account, "custody account")))
            throw new IllegalArgumentException("unknown custody account");
        var row = ledger.accounts().get(account);
        var image = image(ledger); var views = image.available();
        // Every changed account or referenced lot/claim retires its derived
        // balance at image advancement. Unrelated world churn cannot require
        // another complete dependency vector for this unchanged account.
        var available = views.computeIfAbsent(account, ignored -> new Available(balances(ledger, row)));
        return available.quantities().getOrDefault(new Balance(owner, kind), Quantities.ZERO);
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
        if (current != null) {
            ImmutableMapChanges.forEachIdentityChangedKey(current.accounts(), ledger.accounts(), available::remove);
            // Changed records can alter owner/kind/reservation with an entirely
            // unchanged custody row. ALL current declared holders are affected.
            ImmutableMapChanges.forEachIdentityChangedKey(current.lots(), ledger.lots(), id ->
                    FungibleAccountReferences.lotHolders(ledger.accounts(), id).forEach(row -> available.remove(row.id())));
            ImmutableMapChanges.forEachIdentityChangedKey(current.claims(), ledger.claims(), id ->
                    FungibleAccountReferences.claimHolders(ledger.accounts(), id).forEach(row -> available.remove(row.id())));
        }
        current = new Image(ledger.lots(), ledger.claims(), ledger.accounts(), available);
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

    private static Map<Balance, Quantities> balances(FungibleResourceLedger ledger, CustodyAccount account) {
        var balances = new HashMap<Balance, Quantities>();
        for (var entry : account.lotQuantities().entrySet()) {
            var lot = ledger.lots().get(entry.getKey()); int quantity = entry.getValue();
            balances.merge(new Balance(lot.economicOwnerId(), lot.itemKind()), new Quantities(quantity, quantity),
                    (a, b) -> new Quantities(Math.addExact(a.held(), b.held()), Math.addExact(a.available(), b.available())));
        }
        for (var entry : account.claimQuantities().entrySet()) {
            var claim = ledger.claims().get(entry.getKey());
            balances.compute(new Balance(claim.economicOwnerId(), claim.itemKind()), (key, value) -> {
                var stock = value == null ? Quantities.ZERO : value;
                return new Quantities(stock.held(), Math.subtractExact(stock.available(), entry.getValue()));
            });
        }
        return Map.copyOf(balances);
    }
}
