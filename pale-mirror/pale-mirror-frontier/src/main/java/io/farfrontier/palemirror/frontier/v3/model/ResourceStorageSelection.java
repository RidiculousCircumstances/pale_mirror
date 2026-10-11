package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
import java.util.function.Predicate;

/** Read-only discovery. The caller must retain the endpoint and commit its existing capacity demand. */
public final class ResourceStorageSelection {
    public record Placement(ShipmentEndpoint endpoint, int quantity) {
        public Placement {
            Objects.requireNonNull(endpoint);
            if (quantity < 1 || quantity > 64) throw new IllegalArgumentException("storage placement requires a bounded real portion");
        }
    }
    private ResourceStorageSelection() { }
    /** Read-only stock scope uses the same declared ownership/filter/tier as admission. */
    public static Set<SubjectId> containers(FrontierWorldState state, SubjectId owner, String kind, Set<ResourceStorageRole> roles) {
        return declared(state, owner, kind, Set.copyOf(roles))
                .filter(policy -> !ReferenceContainerCustody.blocksCanonicalUse(state, policy.endpoint().containerId()))
                .map(policy -> policy.endpoint().containerId()).collect(java.util.stream.Collectors.toUnmodifiableSet());
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
        Objects.requireNonNull(state); Objects.requireNonNull(owner); Objects.requireNonNull(origin);
        Objects.requireNonNull(completing); Objects.requireNonNull(accessible);
        roles = Set.copyOf(Objects.requireNonNull(roles));
        if (roles.isEmpty()) throw new IllegalArgumentException("storage request must declare its permitted tiers");
        if (quantity < 1 || quantity > 64) throw new IllegalArgumentException("storage selection needs a bounded real portion");
        return ranked(state, owner, itemKind, origin, roles)
                .filter(policy -> !ReferenceContainerCustody.blocksCanonicalUse(state, policy.endpoint().containerId()))
                .filter(policy -> ContainerStorageAdmission.receive(state, policy.endpoint().containerId(), itemKind, quantity, completing))
                .map(ResourceStoragePolicy::endpoint).filter(accessible).findFirst();
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
        return state.inventory().containers().values().stream()
                .filter(record -> record.ownerId().equals(owner)).flatMap(record -> record.storage().stream())
                .filter(policy -> roles.contains(policy.role()))
                .filter(policy -> policy.filter().accepts(itemKind));
    }
    private static long distance(SurfaceAnchor a, SurfaceAnchor b) {
        return Math.abs((long) a.x() - b.x()) + Math.abs((long) a.y() - b.y()) + Math.abs((long) a.z() - b.z());
    }
}
