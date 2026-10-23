package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Shared protected land for development owners. Crop work never recompiles this geometry. */
public final class LandUseExclusions {
    private static final ImmutableResultView<String, Set<TerrainColumn>> VIEW = new ImmutableResultView<>(16);
    public static Set<TerrainColumn> columns(FrontierWorldState state) {
        var inputs = new ArrayList<Object>(List.of(state.bootstrap(), state.routeTopology(), state.hiveColony().addedOrgans()));
        state.resourceSites().cycles().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> inputs.add(entry.getValue().layout()));
        var endpoints = state.inventory().containers().values().stream().filter(container -> container.storage().isPresent())
                .sorted(Comparator.comparing(ContainerRecord::id)).map(container -> container.storage().orElseThrow().endpoint()).toList();
        inputs.addAll(endpoints);
        var access = endpoints.stream().map(endpoint -> ResourceStorageAccess.at(state, endpoint.containerId())).toList();
        access.forEach(value -> inputs.add(value.accessBoundary()));
        // The preparation worker and reducer consume the same immutable land.
        // Share only completed results; no global monitor covers geometry work.
        return VIEW.get(inputs, "columns", () -> {
            var result = new HashSet<TerrainColumn>();
            for (var settlement : state.bootstrap().settlements()) {
                FrontierSettlementActorSlots.intactStructureOccupancy(state.bootstrap().terrain(), settlement.structures())
                        .forEach(position -> add(result, position));
                settlement.residents().forEach(resident -> add(result, resident.home()));
            }
            state.resourceSites().cycles().values().forEach(cycle -> cycle.layout().managedSlots().forEach(p -> add(result, p)));
            var roads = FrontierRouteNetwork.footprint(state.bootstrap(), state.routeTopology());
            roads.foundationCells().forEach(p -> add(result, p)); roads.surfaceCells().forEach(p -> add(result, p));
            FrontierGrayboxPlan.intactOrganOccupancy(state.bootstrap().hive().organs()).forEach(p -> add(result, p));
            FrontierGrayboxPlan.intactOrganOccupancy(List.copyOf(state.hiveColony().addedOrgans().values())).forEach(p -> add(result, p));
            access.forEach(value -> value.ownedAccessSurfaces().forEach(surface -> add(result, surface.support())));
            // Grid coordinates have clustered hashes. HashSet avoids the long
            // linear probe chains of the JDK's compact immutable SetN here.
            return Collections.unmodifiableSet(result);
        });
    }
    private static void add(Set<TerrainColumn> columns, BlockPosition position) {
        columns.add(new TerrainColumn(position.x(), position.z()));
    }
    private LandUseExclusions() { }
}
