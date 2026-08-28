package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;

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
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), next, state.routeTopology(), state.strategicPlans(), state.humanPopulation(), state.resourceSites());
    }

    static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.ROUTE_CONSTRUCTION) throw new IllegalArgumentException("route construction intent kind is invalid");
        RouteConstruction project = intent.subjectIds().stream().map(state.routeConstructions()::get).filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("route construction intent lacks an active project"));
        SubjectId materialId = intent.subjectIds().stream().filter(id -> !id.equals(FrontierRouteNetwork.OWNER) && !id.equals(project.id()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("route construction intent lacks its exact material"));
        ExactItemStack material = state.inventory().items().get(materialId);
        if (project.status() != RouteConstructionStatus.BUILDING || material == null || !material.itemKind().equals(GrayboxMaterial.ROUTE.repairItemKind())
                || !(material.custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(FrontierRouteNetwork.MAINTENANCE_CONTAINER)
                || state.inventory().surfaces().get(slot.containerId()).status() != ContainerSurfaceStatus.ACTIVE) {
            throw new IllegalArgumentException("route construction intent lacks active exact maintenance material");
        }
        if (!wholeBlock(intent).equals(nextCell(state, project))) throw new IllegalArgumentException("route construction intent does not target its next cell");
    }

    static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, RouteConstructionObservation observation,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        validateIntent(state, intent);
        RouteConstruction project = state.routeConstructions().get(observation.projectId());
        if (project == null || !intent.subjectIds().contains(observation.projectId()) || !intent.subjectIds().contains(observation.itemId())
                || !observation.position().equals(nextCell(state, project))) throw new IllegalArgumentException("route construction receipt differs from active work");
        Map<SubjectId, RouteConstruction> projects = new LinkedHashMap<>(state.routeConstructions());
        int confirmed = project.confirmedCells() + 1;
        int required = FrontierRouteNetwork.constructionCells(state.bootstrap(), state.routeTopology(), project.settlementId(), project.waypoints()).size();
        projects.put(project.id(), project.withConfirmedCells(confirmed, confirmed == required ? RouteConstructionStatus.READY : RouteConstructionStatus.BUILDING));
        intents.put(intent.id(), intent.withStatus(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(observation.id())));
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(observation.id(), observation);
        return new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(), state.infection(), state.inventory().consumeOne(observation.itemId()),
                state.productionJobs(), state.contracts(), state.operations(), intents, observations, state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), projects, state.routeTopology(), state.strategicPlans(), state.humanPopulation(), state.resourceSites());
    }

    static FrontierWorldState conflict(FrontierWorldState state, PhysicalIntent intent,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        RouteConstruction project = intent.subjectIds().stream().map(state.routeConstructions()::get).filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("route construction conflict lacks its project"));
        Map<SubjectId, RouteConstruction> projects = new LinkedHashMap<>(state.routeConstructions());
        projects.put(project.id(), project.withConfirmedCells(project.confirmedCells(), RouteConstructionStatus.CONFLICT));
        intents.put(intent.id(), intent.withStatus(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty()));
        return new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(),
                state.contracts(), state.operations(), intents, state.physicalObservations(), state.sceneLeases(), state.hiveColony(), state.structureDamage(),
                state.physicalDeltas(), state.ambientLeases(), projects, state.routeTopology(), state.strategicPlans(), state.humanPopulation(), state.resourceSites());
    }

    static FrontierWorldState cutover(FrontierWorldState state, SubjectId projectId) {
        RouteConstruction project = state.routeConstructions().get(projectId);
        if (project == null || project.status() != RouteConstructionStatus.READY) throw new IllegalArgumentException("route topology cutover requires ready construction");
        List<BlockPosition> required = FrontierRouteNetwork.constructionCells(state.bootstrap(), state.routeTopology(), project.settlementId(), project.waypoints());
        if (project.confirmedCells() != required.size()) throw new IllegalArgumentException("route topology cutover has incomplete physical construction");
        Map<SubjectId, RouteConstruction> projects = new LinkedHashMap<>(state.routeConstructions()); projects.remove(projectId);
        return new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(),
                state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), projects,
                state.routeTopology().replaceSupplyRoute(state.bootstrap(), project.settlementId(), project.waypoints()), state.strategicPlans(), state.humanPopulation(), state.resourceSites());
    }

    static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, RouteConstructionStarted started) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route construction must be owned by the route network");
        return begin(state, started.project());
    }

    static FrontierWorldState reduceCutover(FrontierWorldState state, SubjectId subject, RouteTopologyCutover cutover) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route topology cutover must be owned by the route network");
        return cutover(state, cutover.projectId());
    }

    static void validateReceipt(FrontierBootstrap bootstrap, RouteTopology topology, Map<SubjectId, RouteConstruction> projects,
                                PhysicalIntent intent, RouteConstructionObservation observation) {
        if (intent.kind() != PhysicalIntentKind.ROUTE_CONSTRUCTION || !intent.subjectIds().contains(observation.projectId())
                || !intent.subjectIds().contains(observation.itemId())) throw new IllegalArgumentException("route construction receipt has foreign subjects");
        RouteConstruction project = projects.get(observation.projectId());
        if (project == null || !FrontierRouteNetwork.constructionCells(bootstrap, topology, project.settlementId(), project.waypoints())
                .contains(observation.position())) throw new IllegalArgumentException("route construction receipt is outside its replacement corridor");
    }

    private static BlockPosition nextCell(FrontierWorldState state, RouteConstruction project) {
        return FrontierRouteNetwork.constructionCells(state.bootstrap(), state.routeTopology(), project.settlementId(), project.waypoints()).get(project.confirmedCells());
    }

    private static BlockPosition wholeBlock(PhysicalIntent intent) {
        long scale = io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE;
        if (intent.origin().x().raw() % scale != 0L || intent.origin().y().raw() % scale != 0L || intent.origin().z().raw() % scale != 0L) {
            throw new IllegalArgumentException("route construction origin must be a whole block position");
        }
        return new BlockPosition(Math.toIntExact(intent.origin().x().raw() / scale), Math.toIntExact(intent.origin().y().raw() / scale),
                Math.toIntExact(intent.origin().z().raw() / scale));
    }
}
