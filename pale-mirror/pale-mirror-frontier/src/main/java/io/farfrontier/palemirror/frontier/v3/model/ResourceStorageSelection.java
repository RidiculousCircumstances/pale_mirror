package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
import java.util.function.Predicate;

/** Read-only discovery. The caller must retain the endpoint and commit its existing capacity demand. */
public final class ResourceStorageSelection {
    private record DeclarationImage(Map<SubjectId, ContainerRecord> source,
                                    Map<SubjectId, List<ContainerRecord>> owners) { }
    private record Scope(SubjectId owner, String item, Set<ResourceStorageRole> roles) { }
    private static final ImmutableKeyedInputView<Scope, Set<SubjectId>> ELIGIBLE = new ImmutableKeyedInputView<>(512);
    private static final ThreadLocal<DeclarationImage> DECLARATIONS = new ThreadLocal<>();

    private static List<ContainerRecord> owned(ExactInventory inventory, SubjectId owner) {
        var image = DECLARATIONS.get();
        if (image == null || image.source() != inventory.containers()) {
            var owners = new HashMap<SubjectId, List<ContainerRecord>>();
            for (var record : inventory.containers().values())
                owners.computeIfAbsent(record.ownerId(), ignored -> new ArrayList<>()).add(record);
            owners.replaceAll((id, values) -> List.copyOf(values));
            image = new DeclarationImage(inventory.containers(), Map.copyOf(owners));
            DECLARATIONS.set(image);
        }
        return image.owners().getOrDefault(Objects.requireNonNull(owner), List.of());
    }

    /** Declaration addresses only, including currently blocked custody for causal wake indexing. */
    public static Set<SubjectId> declaredContainers(ExactInventory inventory, SubjectId owner, Set<ResourceStorageRole> roles) {
        roles = Set.copyOf(Objects.requireNonNull(roles));
        if (roles.isEmpty()) throw new IllegalArgumentException("storage request must declare its permitted tiers");
        var requested = roles;
        return owned(inventory, owner).stream().filter(record -> record.storage()
                .filter(policy -> requested.contains(policy.role())).isPresent()).map(ContainerRecord::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public record Placement(ShipmentEndpoint endpoint, int quantity) {
        public Placement {
            Objects.requireNonNull(endpoint);
            if (quantity < 1 || quantity > 64) throw new IllegalArgumentException("storage placement requires a bounded real portion");
        }
    }
    private ResourceStorageSelection() { }
    /** Real unclaimed stock in eligible storage, independent of the task's cargo destination. */
    public record Source(ShipmentEndpoint endpoint, SubjectId accountId, int quantity) {
        public Source {
            Objects.requireNonNull(endpoint); Objects.requireNonNull(accountId);
            if (quantity < 1) throw new IllegalArgumentException("source must contain real available stock");
        }
    }
    public static List<Source> sources(FrontierWorldState state, FungibleResourceLedger resources,
            SubjectId owner, String itemKind, SurfaceAnchor origin, Set<ResourceStorageRole> roles) {
        var result = new ArrayList<Source>();
        for (var endpoint : candidates(state, owner, itemKind, origin, roles)) {
            var account = FungibleResourceCustodySupport.accountAtContainer(state, endpoint.containerId()).orElse(null);
            if (account == null || !resources.accounts().containsKey(account.id())) continue;
            int quantity = resources.unclaimedQuantity(account.id(), owner, itemKind);
            if (quantity > 0) result.add(new Source(endpoint, account.id(), quantity));
        }
        return List.copyOf(result);
    }
    /** Checks a retained declaration without discovering or ranking another endpoint. */
    public static boolean permits(ExactInventory inventory, SubjectId containerId, SubjectId owner,
            String itemKind, Set<ResourceStorageRole> roles) {
        Objects.requireNonNull(inventory); Objects.requireNonNull(containerId); Objects.requireNonNull(owner);
        roles = Set.copyOf(Objects.requireNonNull(roles));
        if (roles.isEmpty()) throw new IllegalArgumentException("storage request must declare its permitted tiers");
        ContainerRecord record = inventory.containers().get(containerId);
        if (record == null || !record.ownerId().equals(owner)) return false;
        ResourceStoragePolicy policy = record.storage().orElse(null);
        return policy != null && roles.contains(policy.role()) && policy.filter().accepts(itemKind);
    }
    /** Exact resources share warehouse policy/custody; slot numbering is not source selection. */
    public static List<ExactItemStack> exactSources(FrontierWorldState state, SubjectId owner, String itemKind,
            int minimum, SurfaceAnchor origin, Set<ResourceStorageRole> roles) {
        if (minimum < 1 || minimum > 64) throw new IllegalArgumentException("exact source requires a bounded real count");
        Map<SubjectId, List<ExactItemStack>> stored = new HashMap<>();
        for (ExactItemStack item : state.inventory().items().values()) {
            if (item.economicOwnerId().equals(owner) && item.itemKind().equals(itemKind) && item.count() >= minimum
                    && item.custody() instanceof InventoryCustody.ContainerSlot slot) {
                stored.computeIfAbsent(slot.containerId(), ignored -> new ArrayList<>()).add(item);
            }
        }
        return candidates(state, owner, itemKind, origin, roles).stream()
                .flatMap(endpoint -> stored.getOrDefault(endpoint.containerId(), List.of()).stream()
                        .sorted(Comparator.comparing(ExactItemStack::id))).toList();
    }
    /** Read-only stock scope uses the same declared ownership/filter/tier as admission. */
    public static Set<SubjectId> containers(FrontierWorldState state, SubjectId owner, String kind, Set<ResourceStorageRole> roles) {
        var scope = new Scope(owner, kind, Set.copyOf(roles));
        return ELIGIBLE.get(state.bootstrap(), scope, List.of(state.inventory().containers(), state.inventory().surfaces(),
                state.replicaCustody(), state.actorLocations()), () -> declared(state, owner, kind, scope.roles())
                    .filter(policy -> !ReferenceContainerCustody.blocksCanonicalUse(state, policy.endpoint().containerId()))
                    .map(policy -> policy.endpoint().containerId()).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }
    public static List<ShipmentEndpoint> declaredCandidates(FrontierWorldState state, SubjectId owner, String kind,
            SurfaceAnchor origin, Set<ResourceStorageRole> roles) {
        return ranked(state, owner, kind, origin, Set.copyOf(roles)).map(ResourceStoragePolicy::endpoint).toList();
    }
    /** Eligible owned storage, before task-specific quantity/route admission. The producer retains its choice. */
    public static List<ShipmentEndpoint> candidates(FrontierWorldState state, SubjectId owner, String kind,
            SurfaceAnchor origin, Set<ResourceStorageRole> roles) {
        return ranked(state, owner, kind, origin, Set.copyOf(roles))
                .filter(policy -> !ReferenceContainerCustody.blocksCanonicalUse(state, policy.endpoint().containerId()))
                .map(ResourceStoragePolicy::endpoint).toList();
    }
    public static Optional<ShipmentEndpoint> destination(FrontierWorldState state, SubjectId owner, String itemKind,
            int quantity, SurfaceAnchor origin, Set<ResourceStorageRole> roles,
            Optional<SubjectId> completing, Predicate<ShipmentEndpoint> accessible) {
        Objects.requireNonNull(accessible);
        return receiving(state, owner, itemKind, quantity, origin, roles, completing)
                .filter(accessible).findFirst();
    }
    /** Capacity only. It neither starts route searches nor grants an interaction/service turn. */
    public static List<ShipmentEndpoint> receivingCandidates(FrontierWorldState state, SubjectId owner, String itemKind,
            int quantity, SurfaceAnchor origin, Set<ResourceStorageRole> roles, Optional<SubjectId> completing) {
        return receiving(state, owner, itemKind, quantity, origin, roles, completing).toList();
    }
    /** A newly issued exact stack needs both capacity and an unoccupied addressed slot. No route search. */
    public static List<ShipmentEndpoint> newStackCandidates(FrontierWorldState state, SubjectId owner, String itemKind,
            int quantity, SurfaceAnchor origin, Set<ResourceStorageRole> roles) {
        return receiving(state, owner, itemKind, quantity, origin, roles, Optional.empty())
                .filter(endpoint -> state.firstFreeContainerSlot(endpoint.containerId()).isPresent()).toList();
    }
    /** Validate one producer-declared receiver without enumerating or ranking alternative warehouses. */
    public static boolean receivesAt(FrontierWorldState state, ShipmentEndpoint endpoint, SubjectId owner,
            String itemKind, int quantity, Set<ResourceStorageRole> roles, Optional<SubjectId> completing) {
        Objects.requireNonNull(endpoint); Objects.requireNonNull(completing);
        if (quantity < 1 || quantity > 64) throw new IllegalArgumentException("receiving needs a bounded real portion");
        if (!permits(state.inventory(), endpoint.containerId(), owner, itemKind, roles)) return false;
        return state.inventory().containers().get(endpoint.containerId()).storage().orElseThrow().endpoint().equals(endpoint)
                && !ReferenceContainerCustody.blocksCanonicalUse(state, endpoint.containerId())
                && ContainerStorageAdmission.receive(state, endpoint.containerId(), itemKind, quantity, completing);
    }
    private static java.util.stream.Stream<ShipmentEndpoint> receiving(FrontierWorldState state, SubjectId owner, String itemKind,
            int quantity, SurfaceAnchor origin, Set<ResourceStorageRole> roles, Optional<SubjectId> completing) {
        Objects.requireNonNull(state); Objects.requireNonNull(owner); Objects.requireNonNull(origin);
        Objects.requireNonNull(completing);
        roles = Set.copyOf(Objects.requireNonNull(roles));
        if (roles.isEmpty()) throw new IllegalArgumentException("storage request must declare its permitted tiers");
        if (quantity < 1 || quantity > 64) throw new IllegalArgumentException("storage selection needs a bounded real portion");
        return ranked(state, owner, itemKind, origin, roles)
                .filter(policy -> !ReferenceContainerCustody.blocksCanonicalUse(state, policy.endpoint().containerId()))
                .filter(policy -> ContainerStorageAdmission.receive(state, policy.endpoint().containerId(), itemKind, quantity, completing))
                .map(ResourceStoragePolicy::endpoint);
    }
    /** Work issuing a new physical stack needs an empty addressed slot as well as volume. */
    public static Optional<ShipmentEndpoint> newStackDestination(FrontierWorldState state, SubjectId owner, String itemKind,
            int quantity, SurfaceAnchor origin, Set<ResourceStorageRole> roles, Predicate<ShipmentEndpoint> accessible) {
        Objects.requireNonNull(accessible);
        return receiving(state, owner, itemKind, quantity, origin, roles, Optional.empty())
                .filter(endpoint -> state.firstFreeContainerSlot(endpoint.containerId()).isPresent())
                .filter(accessible).findFirst();
    }
    /** Hauling may fill available capacity with a real partial batch, rather than waiting for a full stack. */
    public static Optional<Placement> destinationUpTo(FrontierWorldState state, SubjectId owner, String kind, int maximum,
            SurfaceAnchor origin, Set<ResourceStorageRole> roles, Predicate<Placement> accessible) {
        Objects.requireNonNull(state); Objects.requireNonNull(owner); Objects.requireNonNull(origin); Objects.requireNonNull(accessible);
        if (maximum < 1 || maximum > 64) throw new IllegalArgumentException("storage selection needs a bounded real portion");
        return ranked(state, owner, kind, origin, Set.copyOf(roles))
                .filter(policy -> !ReferenceContainerCustody.blocksCanonicalUse(state, policy.endpoint().containerId())).map(policy -> {
            int quantity = maximum;
            while (quantity > 0 && !ContainerStorageAdmission.receive(state, policy.endpoint().containerId(), kind, quantity, Optional.empty())) quantity--;
            return quantity == 0 ? null : new Placement(policy.endpoint(), quantity);
        }).filter(Objects::nonNull).filter(accessible).findFirst();
    }
    private static java.util.stream.Stream<ResourceStoragePolicy> ranked(FrontierWorldState state, SubjectId owner, String itemKind,
            SurfaceAnchor origin, Set<ResourceStorageRole> roles) {
        return declared(state, owner, itemKind, roles)
                .sorted(Comparator.comparingInt(ResourceStoragePolicy::priority).reversed()
                        .thenComparingLong(policy -> distance(origin, policy.endpoint().station()))
                        .thenComparing(policy -> policy.endpoint().containerId()));
    }
    private static java.util.stream.Stream<ResourceStoragePolicy> declared(FrontierWorldState state, SubjectId owner,
            String itemKind, Set<ResourceStorageRole> roles) {
        if (roles.isEmpty()) throw new IllegalArgumentException("storage request must declare its permitted tiers");
        return owned(state.inventory(), owner).stream().flatMap(record -> record.storage().stream())
                .filter(policy -> roles.contains(policy.role()))
                .filter(policy -> policy.filter().accepts(itemKind));
    }
    private static long distance(SurfaceAnchor a, SurfaceAnchor b) {
        return Math.abs((long) a.x() - b.x()) + Math.abs((long) a.y() - b.y()) + Math.abs((long) a.z() - b.z());
    }
}
