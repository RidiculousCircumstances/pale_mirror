package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded invariants for inactive construction work; no project is a route until cutover. */
final class RouteConstructionStateSupport {
    static final int MAX_CONSTRUCTIONS = 12;
    private RouteConstructionStateSupport() { }

    static void validate(FrontierBootstrap bootstrap, RouteTopology topology, Map<SubjectId, RouteConstruction> constructions) {
        if (constructions.size() > MAX_CONSTRUCTIONS) throw new IllegalArgumentException("route construction retention limit exceeded");
        HashSet<SubjectId> settlements = new HashSet<>();
        for (Map.Entry<SubjectId, RouteConstruction> entry : constructions.entrySet()) {
            RouteConstruction project = entry.getValue();
            if (!entry.getKey().equals(project.id()) || !settlements.add(project.settlementId())) {
                throw new IllegalArgumentException("route construction identity or settlement is duplicated");
            }
            FrontierRouteNetwork.validateSupplyWaypoints(bootstrap, project.settlementId(), project.waypoints());
            List<BlockPosition> required = FrontierRouteNetwork.constructionCells(bootstrap, topology, project.settlementId(), project.waypoints());
            if (required.isEmpty() || required.size() > 65_535 || project.confirmedCells() > required.size()) throw new IllegalArgumentException("route construction cursor is invalid");
            if (project.status() == RouteConstructionStatus.READY != (project.confirmedCells() == required.size())) {
                throw new IllegalArgumentException("route construction readiness does not match confirmed work");
            }
        }
    }

    static FrontierWorldState begin(FrontierWorldState state, RouteConstruction project) {
        if (state.routeConstructions().containsKey(project.id()) || state.routeConstructions().values().stream()
                .anyMatch(current -> current.settlementId().equals(project.settlementId()))) {
            throw new IllegalArgumentException("route construction is already active for this identity or settlement");
        }
        Map<SubjectId, RouteConstruction> next = new LinkedHashMap<>(state.routeConstructions()); next.put(project.id(), project);
        return new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(),
                state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), next, state.routeTopology());
    }
}
