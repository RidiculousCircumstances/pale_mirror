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
    private record Membership(ResourceCustody custody, boolean claimed) { }
    private record Layout(Map<ResourceCustody, List<SubjectId>> holders,
                          Map<SubjectId, List<SubjectId>> containers, Map<SubjectId, List<SubjectId>> actors,
                          Map<ResourceCustody.Container, SubjectId> sections,
                          List<SubjectId> ground, Set<ResourceCustody> claimed) { }
    private sealed interface Query permits HolderQuery, ContainerQuery, ActorQuery, GroundQuery { }
    private record HolderQuery(ResourceCustody custody) implements Query { }
    private record ContainerQuery(SubjectId container) implements Query { }
    private record ActorQuery(SubjectId actor) implements Query { }
    private enum GroundQuery implements Query { INSTANCE }
    /** Only queried groups materialize current rows. Quantity changes never
     * rewrite the world-wide membership directory or reuse an old account value. */
    private static final class Custodies {
        final Map<SubjectId, CustodyAccount> accounts;
        final Layout layout;
        final BoundedValueMemo<Query, List<CustodyAccount>> rows = new BoundedValueMemo<>(FungibleResourceLedger.MAX_ACCOUNTS * 3 + 1);
        Custodies(Map<SubjectId, CustodyAccount> accounts, Layout layout) { this.accounts = accounts; this.layout = layout; }
        CustodyAccount account(SubjectId id) { return Objects.requireNonNull(accounts.get(id), "custody layout lost its current account"); }
        List<CustodyAccount> read(Query query, List<SubjectId> ids) {
            return rows.get(query, () -> ids.stream().map(this::account).toList());
        }
    }
    private static final ImmutableResultView<Boolean, Custodies> CUSTODIES = new ImmutableResultView<>(4);
    private static final ImmutableResultView<Boolean, Layout> LAYOUTS = new ImmutableResultView<>(4);
    private static final ImmutableRegistryView<SubjectId, CustodyAccount, Membership> MEMBERSHIPS =
            new ImmutableRegistryView<>(FungibleResourceLedger.MAX_ACCOUNTS, Objects::equals);
    private static final ImmutableKeyOrderView<SubjectId> ACCOUNT_ORDER = new ImmutableKeyOrderView<>(FungibleResourceLedger.MAX_ACCOUNTS);
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
        var image = custodies(ledger);
        var id = image.layout.sections().get(new ResourceCustody.Container(Objects.requireNonNull(container), Objects.requireNonNull(section)));
        return id == null ? Optional.empty() : Optional.of(image.account(id));
    }

    /** All retained accounts, not a first-account shortcut for physical capacity. */
    static List<CustodyAccount> containerAccounts(FungibleResourceLedger ledger, SubjectId container) {
        var image = custodies(ledger);
        return image.read(new ContainerQuery(container), image.layout.containers().getOrDefault(container, List.of()));
    }

    static boolean carriesClaim(FungibleResourceLedger ledger, ResourceCustody holder) {
        return custodies(ledger).layout.claimed().contains(holder);
    }
    static List<CustodyAccount> accountsAt(FungibleResourceLedger ledger, ResourceCustody holder) {
        var image = custodies(ledger);
        return image.read(new HolderQuery(Objects.requireNonNull(holder)), image.layout.holders().getOrDefault(holder, List.of()));
    }
    static List<CustodyAccount> actorAccounts(FungibleResourceLedger ledger, SubjectId actor) {
        var image = custodies(ledger);
        return image.read(new ActorQuery(Objects.requireNonNull(actor)), image.layout.actors().getOrDefault(actor, List.of()));
    }
    static List<CustodyAccount> groundAccounts(FungibleResourceLedger ledger) {
        var image = custodies(ledger);
        return image.read(GroundQuery.INSTANCE, image.layout.ground());
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

    /** One complete read-only custody image. It grants neither title nor availability.
     * Revisions and independent immutable branches never share a mutable index. */
    private static Custodies custodies(FungibleResourceLedger ledger) {
        return CUSTODIES.get(List.of(ledger.accounts()), Boolean.TRUE, () -> {
            // This projection has one read-only algorithm epoch, not a world or
            // execution epoch. Its complete inputs are account ID/custody/claim
            // presence. Actual quantities always come from the captured ledger.
            var members = MEMBERSHIPS.project(MEMBERSHIPS, ledger.accounts(),
                    (id, account) -> new Membership(account.custody(), !account.claimQuantities().isEmpty()));
            var order = ACCOUNT_ORDER.get(ledger.accounts());
            return new Custodies(ledger.accounts(), LAYOUTS.get(List.of(members, order), Boolean.TRUE, () -> layout(members, order)));
        });
    }
    private static Layout layout(Map<SubjectId, Membership> members, List<SubjectId> order) {
        var holders = new HashMap<ResourceCustody, List<SubjectId>>();
        var containers = new HashMap<SubjectId, List<SubjectId>>();
        var actors = new HashMap<SubjectId, List<SubjectId>>();
        var sections = new HashMap<ResourceCustody.Container, SubjectId>();
        var ground = new ArrayList<SubjectId>(); var claimed = new HashSet<ResourceCustody>();
        for (var id : order) {
            var member = members.get(id);
            holders.computeIfAbsent(member.custody(), ignored -> new ArrayList<>()).add(id);
            if (member.claimed()) claimed.add(member.custody());
            if (member.custody() instanceof ResourceCustody.Container holder) {
                if (sections.putIfAbsent(holder, id) != null)
                    throw new IllegalArgumentException("container section has competing resource accounts: " + holder);
                containers.computeIfAbsent(holder.containerId(), ignored -> new ArrayList<>()).add(id);
            } else if (member.custody() instanceof ResourceCustody.Actor holder)
                actors.computeIfAbsent(holder.actorId(), ignored -> new ArrayList<>()).add(id);
            else if (member.custody() instanceof ResourceCustody.Ground) ground.add(id);
        }
        holders.replaceAll((holder, accounts) -> List.copyOf(accounts)); // Preserve first-at-custody query order.
        containers.replaceAll((id, accounts) -> accounts.stream().sorted().toList());
        actors.replaceAll((id, accounts) -> accounts.stream().sorted().toList());
        ground.sort(Comparator.naturalOrder());
        return new Layout(ImmutableLookupMap.copyOf(holders), ImmutableLookupMap.copyOf(containers), ImmutableLookupMap.copyOf(actors), ImmutableLookupMap.copyOf(sections),
                List.copyOf(ground), Set.copyOf(claimed));
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
