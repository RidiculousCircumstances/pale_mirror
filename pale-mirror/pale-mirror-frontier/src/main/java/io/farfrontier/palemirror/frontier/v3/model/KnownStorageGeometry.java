package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Rebuildable physical footprint only; stock, filters and access status are not terrain. */
final class KnownStorageGeometry {
    private static FrontierBootstrap bootstrap;
    private static Map<SubjectId, ContainerRecord> records;
    private static Map<SubjectId, ContainerSurface> surfaces;
    private static Set<BlockPosition> obstacles = Set.of();
    private static Set<BlockPosition> fixedSupports = Set.of();
    private KnownStorageGeometry() { }

    static synchronized Set<BlockPosition> forState(FrontierWorldState state) {
        var currentRecords = state.inventory().containers();
        var currentSurfaces = state.inventory().surfaces();
        if (bootstrap == state.bootstrap() && records == currentRecords && surfaces == currentSurfaces) return obstacles;
        var projected = new HashSet<BlockPosition>();
        var contacts = new HashSet<BlockPosition>();
        currentSurfaces.values().stream().filter(ContainerSurface::fixed)
                .filter(surface -> surface.status() != ContainerSurfaceStatus.RETIRED)
                .forEach(surface -> {
                    contacts.add(surface.position());
                    // Authored containers already belong to structural occupancy;
                    // dynamically declared storage contributes its extra footprint.
                    if (currentRecords.get(surface.containerId()).purpose() == ContainerPurpose.RESOURCE_STORAGE)
                        projected.add(surface.position());
                });
        if (bootstrap != state.bootstrap() || !obstacles.equals(projected)) obstacles = Set.copyOf(projected);
        if (bootstrap != state.bootstrap() || !fixedSupports.equals(contacts)) fixedSupports = Set.copyOf(contacts);
        bootstrap = state.bootstrap(); records = currentRecords; surfaces = currentSurfaces;
        return obstacles;
    }
    /** Physical contact does not depend on the container's economic purpose. */
    static synchronized Set<BlockPosition> fixedSupports(FrontierWorldState state) {
        forState(state);
        return fixedSupports;
    }
}
