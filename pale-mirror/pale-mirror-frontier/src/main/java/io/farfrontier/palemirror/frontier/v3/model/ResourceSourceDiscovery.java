package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach;
import java.util.*;

/** Source adapters expose existing custody, never mint stock or infer title from location. */
public final class ResourceSourceDiscovery {
    public static List<ResourceSourceEndpoint> localHaulSources(FrontierWorldState state, SubjectId home) {
        var result = new ArrayList<ResourceSourceEndpoint>();
        ResourceStorageSelection.declaredContainers(state.inventory(), home, Set.of(ResourceStorageRole.LOCAL_BUFFER)).stream()
                .map(state.inventory().containers()::get)
                .flatMap(container -> container.storage().stream()).filter(policy -> policy.role() == ResourceStorageRole.LOCAL_BUFFER)
                .sorted(Comparator.comparing(policy -> policy.endpoint().containerId())).forEach(policy -> result.add(policy.endpoint()));
        var resources = state.inventory().fungibleResources();
        var grounds = FungibleResourceReadIndex.groundAccounts(resources).stream().filter(account -> !account.lotQuantities().isEmpty()
                && account.lotQuantities().keySet().stream().anyMatch(id -> resources.lots().get(id).economicOwnerId().equals(home))).toList();
        if (grounds.isEmpty()) return List.copyOf(result);
        var knowledge = KnownPedestrianRouteKnowledge.forPublicJourney(state, home, List.of());
        for (var account : grounds) {
            var ground = (ResourceCustody.Ground) account.custody();
            var bindings = FungibleBindingReadIndex.forAccount(resources, account.id());
            if (bindings.size() != 1 || !(bindings.getFirst().address() instanceof PhysicalStackAddress.WorldEntity address)
                    || !address.entityId().equals(ground.entityId())) continue;
            var stations = new ArrayList<SurfaceAnchor>();
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                for (var station : knowledge.surfacesAt(ground.position().x() + dx, ground.position().z() + dz)) {
                    if (!knowledge.geometry().blocked(station)
                            && BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN.contains(station, ground.position())) stations.add(station);
                }
            }
            stations.sort(Comparator.comparingLong((SurfaceAnchor station) -> Math.abs((long) station.x() - ground.position().x())
                    + Math.abs((long) station.y() - ground.position().y()) + Math.abs((long) station.z() - ground.position().z()))
                    .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z));
            for (var station : stations) result.add(new ResourceSourceEndpoint.Ground(home, ground, station, bindings.getFirst().authorityEpoch()));
        }
        return List.copyOf(result);
    }
    private ResourceSourceDiscovery() { }
}
