package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalColumnSet;

/** Shared protected land for development owners. Crop work never recompiles this geometry. */
public final class LandUseExclusions {
    private record AccessImage(List<ServiceAccessBoundary> boundaries) {
        private AccessImage { boundaries = List.copyOf(boundaries); }
    }
    private static final ImmutableResultView<AccessImage, RegionalColumnSet> VIEW = new ImmutableResultView<>(16);
    private static final ImmutableResultView<String, RegionalColumnSet> BASE = new ImmutableResultView<>(4), ROADS = new ImmutableResultView<>(4), ORGANS = new ImmutableResultView<>(4);
    private static final ImmutableRegistryView<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ResourceFieldLayout, RegionalColumnSet>
            FIELDS = new ImmutableRegistryView<>(ResourceSiteState.MAX_SITES);
    public static RegionalColumnSet columns(FrontierWorldState state) {
        var base = BASE.get(List.of(state.bootstrap()), "initial", () -> {
            var positions = new HashSet<BlockPosition>();
            for (var settlement : state.bootstrap().settlements()) {
                positions.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(state.bootstrap().terrain(), settlement.structures()));
                settlement.residents().forEach(resident -> positions.add(resident.home()));
            }
            positions.addAll(FrontierGrayboxPlan.intactOrganOccupancy(state.bootstrap().hive().organs()));
            return RegionalColumnSet.fromPositions(positions);
        });
        var roads = ROADS.get(List.of(state.bootstrap(), state.routeTopology()), "routes", () -> {
            var footprint = FrontierRouteNetwork.footprint(state.bootstrap(), state.routeTopology());
            var positions = new HashSet<>(footprint.foundationCells()); positions.addAll(footprint.surfaceCells());
            return RegionalColumnSet.fromPositions(positions);
        });
        var organs = ORGANS.get(List.of(state.hiveColony().addedOrgans()), "added", () -> RegionalColumnSet.fromPositions(
                FrontierGrayboxPlan.intactOrganOccupancy(List.copyOf(state.hiveColony().addedOrgans().values()))));
        var parts = new ArrayList<>(List.of(base, roads, organs));
        var layouts = new HashMap<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ResourceFieldLayout>();
        state.resourceSites().cycles().forEach((id, cycle) -> layouts.put(id, cycle.layout()));
        FIELDS.project(state.bootstrap(), layouts, (id, layout) -> RegionalColumnSet.fromPositions(layout.managedSlots()))
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> parts.add(entry.getValue()));
        var inputs = new ArrayList<Object>(parts);
        var endpoints = state.inventory().containers().values().stream().filter(container -> container.storage().isPresent())
                .sorted(Comparator.comparing(ContainerRecord::id)).map(container -> container.storage().orElseThrow().endpoint()).toList();
        inputs.addAll(endpoints);
        var access = endpoints.stream().map(endpoint -> ServiceAccessCoordinator.boundary(state,endpoint.containerId())).toList();
        // Declaration-cache refresh can reconstruct equal access boundaries.
        // Their object identity is not a land revision; retain complete values
        // in the query while canonical owner inputs remain identity fenced.
        var accessImage = new AccessImage(access);
        // The preparation worker and reducer consume the same immutable land.
        // Share only completed results; no global monitor covers geometry work.
        return VIEW.get(inputs, accessImage, () -> {
            parts.add(RegionalColumnSet.fromPositions(access.stream().flatMap(value -> value.occupiedSurfaces().stream())
                    .map(SurfaceAnchor::support).toList()));
            return RegionalColumnSet.union(parts);
        });
    }
    private LandUseExclusions() { }
}
