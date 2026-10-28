package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Disposable queries of the sole immutable ledger; never allocation or custody authority. */
final class FungibleResourceReadIndex {
    private record Balance(SubjectId owner, String kind) { }
    private record Available(CustodyAccount account, ImmutableDependencies dependencies, Map<Balance, Integer> quantities) { }
    private record Image(Map<SubjectId, ResourceLot> lots, Map<SubjectId, ClaimAllocation> claims,
                         Map<SubjectId, CustodyAccount> accounts,
                         Map<SubjectId, Available> available) { }
    private record Custodies(Map<ResourceCustody, List<CustodyAccount>> holders,
                             Map<SubjectId, List<CustodyAccount>> containers,
                             Map<SubjectId, List<CustodyAccount>> actors,
                             Map<ResourceCustody.Container, CustodyAccount> sections,
                             List<CustodyAccount> ground, Set<ResourceCustody> claimed) { }
    private static final ImmutableResultView<Boolean, Custodies> CUSTODIES = new ImmutableResultView<>(4);
    // Retain one immutable image per owner thread, not accumulated world revisions.
    private static final ThreadLocal<Image> CURRENT = new ThreadLocal<>();
    private FungibleResourceReadIndex() { }

    static int available(FungibleResourceLedger ledger, SubjectId account, SubjectId owner, String kind) {
        Objects.requireNonNull(owner, "resource economic owner"); Objects.requireNonNull(kind, "resource item kind");
        if (!ledger.accounts().containsKey(Objects.requireNonNull(account, "custody account")))
            throw new IllegalArgumentException("unknown custody account");
        var row = ledger.accounts().get(account);
        var views = image(ledger).available();
        var inputs = new Object[1 + row.lotQuantities().size() + row.claimQuantities().size()];
        int index = 0; inputs[index++] = row;
        for (var id : row.lotQuantities().keySet()) inputs[index++] = ledger.lots().get(id);
        for (var id : row.claimQuantities().keySet()) inputs[index++] = ledger.claims().get(id);
        var previous = views.get(account);
        if (previous == null || !previous.dependencies().matches(inputs)) {
            var quantities = balances(ledger, row);
            previous = new Available(row, ImmutableDependencies.of(inputs), quantities);
            views.put(account, previous);
        }
        return previous.quantities().getOrDefault(new Balance(owner, kind), 0);
    }

    static Optional<CustodyAccount> container(FungibleResourceLedger ledger, SubjectId container) {
        return container(ledger, container, ContainerSectionId.STOCK);
    }

    static Optional<CustodyAccount> container(FungibleResourceLedger ledger, SubjectId container, ContainerSectionId section) {
        return Optional.ofNullable(sections(ledger).get(new ResourceCustody.Container(
                Objects.requireNonNull(container), Objects.requireNonNull(section))));
    }

    /** All retained accounts, not a first-account shortcut for physical capacity. */
    static List<CustodyAccount> containerAccounts(FungibleResourceLedger ledger, SubjectId container) {
        return custodies(ledger).containers().getOrDefault(container, List.of());
    }

    static boolean carriesClaim(FungibleResourceLedger ledger, ResourceCustody holder) {
        return custodies(ledger).claimed().contains(holder);
    }
    static List<CustodyAccount> accountsAt(FungibleResourceLedger ledger, ResourceCustody holder) {
        return custodies(ledger).holders().getOrDefault(Objects.requireNonNull(holder), List.of());
    }
    static List<CustodyAccount> actorAccounts(FungibleResourceLedger ledger, SubjectId actor) {
        return custodies(ledger).actors().getOrDefault(Objects.requireNonNull(actor), List.of());
    }
    static List<CustodyAccount> groundAccounts(FungibleResourceLedger ledger) {
        return custodies(ledger).ground();
    }

    private static Image image(FungibleResourceLedger ledger) {
        var current = CURRENT.get();
        if (current != null && current.lots() == ledger.lots() && current.claims() == ledger.claims()
                && current.accounts() == ledger.accounts()) return current;
        // Only queried rows of the current active registry are retained. A local
        // claim change must not discard every other account's balance. Replaced or
        // withdrawn accounts (including a new world) release their old evidence.
        var available = current == null ? new HashMap<SubjectId, Available>() : current.available();
        available.entrySet().removeIf(entry -> ledger.accounts().get(entry.getKey()) != entry.getValue().account());
        current = new Image(ledger.lots(), ledger.claims(), ledger.accounts(), available);
        CURRENT.set(current);
        return current;
    }

    /** Section lookup depends only on custody rows. Comparing pre/post stock
     * snapshots must not rebuild the whole ledger index for each container. */
    private static Map<ResourceCustody.Container, CustodyAccount> sections(FungibleResourceLedger ledger) {
        return custodies(ledger).sections();
    }
    /** One complete read-only custody image. It grants neither title nor availability.
     * Revisions and independent immutable branches never share a mutable index. */
    private static Custodies custodies(FungibleResourceLedger ledger) {
        return CUSTODIES.get(List.of(ledger.accounts()), Boolean.TRUE, () -> {
            var holders = new HashMap<ResourceCustody, List<CustodyAccount>>();
            var containers = new HashMap<SubjectId, List<CustodyAccount>>();
            var actors = new HashMap<SubjectId, List<CustodyAccount>>();
            var sections = new HashMap<ResourceCustody.Container, CustodyAccount>();
            var ground = new ArrayList<CustodyAccount>(); var claimed = new HashSet<ResourceCustody>();
            for (var account : ledger.accounts().values()) {
                holders.computeIfAbsent(account.custody(), ignored -> new ArrayList<>()).add(account);
                if (!account.claimQuantities().isEmpty()) claimed.add(account.custody());
                if (account.custody() instanceof ResourceCustody.Container holder) {
                    if (sections.putIfAbsent(holder, account) != null)
                        throw new IllegalArgumentException("container section has competing resource accounts: " + holder);
                    containers.computeIfAbsent(holder.containerId(), ignored -> new ArrayList<>()).add(account);
                } else if (account.custody() instanceof ResourceCustody.Actor holder)
                    actors.computeIfAbsent(holder.actorId(), ignored -> new ArrayList<>()).add(account);
                else if (account.custody() instanceof ResourceCustody.Ground) ground.add(account);
            }
            holders.replaceAll((holder, accounts) -> List.copyOf(accounts)); // Preserve first-at-custody query order.
            containers.replaceAll((id, accounts) -> accounts.stream().sorted(Comparator.comparing(CustodyAccount::id)).toList());
            actors.replaceAll((id, accounts) -> accounts.stream().sorted(Comparator.comparing(CustodyAccount::id)).toList());
            ground.sort(Comparator.comparing(CustodyAccount::id));
            return new Custodies(Map.copyOf(holders), Map.copyOf(containers), Map.copyOf(actors), Map.copyOf(sections),
                    List.copyOf(ground), Set.copyOf(claimed));
        });
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
