package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic physical targets for the bootstrap's exact container catalog. */
final class ContainerSurfaceManifest {
    private ContainerSurfaceManifest() { }

    static Map<SubjectId, ContainerSurface> initial(FrontierBootstrap bootstrap) {
        Map<SubjectId, ContainerSurface> surfaces = new LinkedHashMap<>();
        bootstrap.settlements().forEach(settlement -> settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().ifPresent(depot -> {
                    SubjectId container = depotId(settlement.id());
                    surfaces.put(container, unmaterialized(container, depot.anchor()));
                }));
        bootstrap.hive().organs().forEach(organ -> organ.containerId().ifPresent(container ->
                surfaces.put(container, unmaterialized(container, organ.anchor()))));
        surfaces.put(FrontierRouteNetwork.MAINTENANCE_CONTAINER,
                new ContainerSurface(FrontierRouteNetwork.MAINTENANCE_CONTAINER,
                        FrontierRouteNetwork.maintenanceContainerPosition(bootstrap), ContainerSurfaceStatus.UNMATERIALIZED));
        return Map.copyOf(surfaces);
    }

    private static ContainerSurface unmaterialized(SubjectId container, BlockPosition anchor) {
        return new ContainerSurface(container, new BlockPosition(anchor.x(), anchor.y() + 1, anchor.z()), ContainerSurfaceStatus.UNMATERIALIZED);
    }

    private static SubjectId depotId(SubjectId settlementId) {
        return new SubjectId("container:" + settlementId.value().substring("settlement:".length()) + "-depot");
    }
}
