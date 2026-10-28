package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Rebuildable physical footprint only; stock, filters and access status are not terrain. */
final class KnownStorageGeometry {
    private static final class Working {
        FrontierBootstrap bootstrap;
        Map<SubjectId, ContainerRecord> records;
        Map<SubjectId, ContainerSurface> surfaces;
        Set<BlockPosition> obstacles = Set.of();
        Set<BlockPosition> fixedSupports = Set.of();
    }
    private static final ThreadLocal<Working> CURRENT = ThreadLocal.withInitial(Working::new);
    private KnownStorageGeometry() { }

    static Set<BlockPosition> forState(FrontierWorldState state) {
        var working = CURRENT.get();
        var currentRecords = state.inventory().containers();
        var currentSurfaces = state.inventory().surfaces();
        if (working.bootstrap == state.bootstrap() && working.records == currentRecords && working.surfaces == currentSurfaces) return working.obstacles;
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
        if (working.bootstrap != state.bootstrap() || !working.obstacles.equals(projected)) working.obstacles = Set.copyOf(projected);
        if (working.bootstrap != state.bootstrap() || !working.fixedSupports.equals(contacts)) working.fixedSupports = Set.copyOf(contacts);
        working.bootstrap = state.bootstrap(); working.records = currentRecords; working.surfaces = currentSurfaces;
        return working.obstacles;
    }
    /** Physical contact does not depend on the container's economic purpose. */
    static Set<BlockPosition> fixedSupports(FrontierWorldState state) {
        forState(state);
        return CURRENT.get().fixedSupports;
    }
}
