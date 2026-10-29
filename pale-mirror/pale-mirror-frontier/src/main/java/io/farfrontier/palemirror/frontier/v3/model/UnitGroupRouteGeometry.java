package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroup;
import io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState;
import io.farfrontier.palemirror.frontier.v3.model.navigation.RouteSpatialIndex;
import java.util.List;
import java.util.Optional;

/** Current declared group route geometry. Derived indices retire with their
 * producer rows; moving a member does not rebuild an unchanged route. */
public final class UnitGroupRouteGeometry {
    private static final ImmutableRegistryView<SubjectId, UnitGroup, List<SurfaceAnchor>> ROUTES =
            new ImmutableRegistryView<>(UnitGroupState.MAX_GROUPS);
    private static final class Geometry {
        private final List<SurfaceAnchor> route;
        private RouteSpatialIndex index;
        Geometry(List<SurfaceAnchor> route) { this.route = route; }
        RouteSpatialIndex read() {
            if (index == null) index = new RouteSpatialIndex(route);
            return index;
        }
    }
    // Registry images are computing-thread-local. Unqueried groups retain only
    // their declaration, never an eagerly built world-wide geometry portfolio.
    private static final ImmutableRegistryView<SubjectId, List<SurfaceAnchor>, Optional<Geometry>> INDICES =
            new ImmutableRegistryView<>(UnitGroupState.MAX_GROUPS);
    private UnitGroupRouteGeometry() { }
    public static RouteSpatialIndex read(FrontierWorldState state, UnitGroup group) {
        var route = group.journey().orElseThrow().route();
        var routes = ROUTES.project(state.bootstrap(), state.unitGroups().groups(),
                (id, row) -> row.journey().map(UnitGroup.Journey::route).orElseGet(List::of));
        // Provisional/earlier immutable declarations may be assessed by the
        // owning planner. Compute their exact geometry, never substitute a
        // different currently retained group's route or grant it any authority.
        if (routes.get(group.id()) != route) return new RouteSpatialIndex(route);
        return INDICES.project(state.bootstrap(), routes, (id, path) -> path.isEmpty()
                ? Optional.empty() : Optional.of(new Geometry(path))).get(group.id()).orElseThrow().read();
    }
}
