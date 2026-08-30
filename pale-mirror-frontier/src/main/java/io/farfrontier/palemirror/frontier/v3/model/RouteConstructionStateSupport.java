package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;

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
                state.contracts(), state.operations(), state.logisticsHistory(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), next, state.routeTopology(), state.strategicPlans(), state.humanPopulation(), state.resourceSites());
    }

    static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.ROUTE_CONSTRUCTION) throw new IllegalArgumentException("route construction intent kind is invalid");
        RouteConstruction project = intent.subjectIds().stream().map(state.routeConstructions()::get).filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("route construction intent lacks an active project"));
        SubjectId cargoId = project.cargoId().orElseThrow(() -> new IllegalArgumentException("route construction intent has no loaded material cargo"));
        if (!intent.subjectIds().contains(cargoId)) throw new IllegalArgumentException("route construction intent lacks its material cargo");
        SubjectId materialId = intent.subjectIds().stream().filter(id -> !id.equals(FrontierRouteNetwork.OWNER) && !id.equals(project.id()) && !id.equals(cargoId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("route construction intent lacks its exact material"));
        ExactItemStack material = state.inventory().items().get(materialId);
        if (project.status() != RouteConstructionStatus.BUILDING || material == null || !material.itemKind().equals(GrayboxMaterial.ROUTE.repairItemKind())
                || !material.custody().equals(new InventoryCustody.Cargo(cargoId)) || state.inventory().cargo().get(cargoId) == null
                || !materialId.equals(RouteConstructionProcess.cargoItemId(project))
                || !state.inventory().cargo().get(cargoId).itemIds().equals(java.util.List.of(materialId))) {
            throw new IllegalArgumentException("route construction intent lacks exact COLD work cargo");
        }
        if (!wholeBlock(intent).equals(nextCell(state, project))) throw new IllegalArgumentException("route construction intent does not target its next cell");
    }

    static void validateMaterialLoadingIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING || !intent.subjectIds().contains(FrontierRouteNetwork.OWNER)
                || intent.subjectIds().size() != 5 || !intent.causeSubjectId().equals(intent.subjectIds().get(1))) {
            throw new IllegalArgumentException("route construction material loading has invalid ownership");
        }
        RouteConstruction project = state.routeConstructions().get(intent.causeSubjectId());
        if (project == null || project.status() != RouteConstructionStatus.BUILDING || project.cargoId().isPresent()) {
            throw new IllegalArgumentException("route construction material loading has no unassigned active project");
        }
        SubjectId cargoId = intent.subjectIds().get(2), cargoItemId = intent.subjectIds().get(3), itemId = intent.subjectIds().get(4);
        if (!cargoId.equals(RouteConstructionProcess.cargoId(project)) || state.inventory().cargo().containsKey(cargoId)
                || !cargoItemId.equals(RouteConstructionProcess.cargoItemId(project)) || state.inventory().items().containsKey(cargoItemId)) {
            throw new IllegalArgumentException("route construction material loading has invalid cargo identity");
        }
        ExactItemStack item = state.inventory().items().get(itemId);
        if (item == null || item.count() < 1 || !item.itemKind().equals(GrayboxMaterial.ROUTE.repairItemKind())
                || !(item.custody() instanceof InventoryCustody.ContainerSlot slot) || !slot.containerId().equals(FrontierRouteNetwork.MAINTENANCE_CONTAINER)
                || state.inventory().surfaces().get(slot.containerId()).status() != ContainerSurfaceStatus.ACTIVE) {
            throw new IllegalArgumentException("route construction material loading lacks an active exact maintenance stack");
        }
        if (!wholeBlock(intent).equals(FrontierRouteNetwork.maintenanceContainerPosition(state.bootstrap()))) {
            throw new IllegalArgumentException("route construction material loading origin is not its maintenance chest");
        }
    }

    static void validateMaterialLoadingReceipt(FrontierWorldState state, PhysicalIntent intent, RouteConstructionMaterialLoadObservation observation) {
        validateMaterialLoadingIntent(state, intent);
        ExactItemStack item = state.inventory().items().get(observation.sourceItemId());
        if (!intent.id().equals(observation.intentId()) || !intent.causeSubjectId().equals(observation.projectId())
                || !intent.subjectIds().get(2).equals(observation.cargoId()) || !intent.subjectIds().get(3).equals(observation.cargoItemId())
                || item == null || !item.id().equals(intent.subjectIds().get(4))
                || observation.sourceRemainingCount() != item.count() - 1) {
            throw new IllegalArgumentException("route construction material receipt does not match its prepared pickup");
        }
    }

    /**
     * Snapshot/WAL validation may observe either reducer boundary of the one
     * atomic pickup transaction: the pre-conversion exact slot or the resulting
     * COLD cargo. A persisted recovery image can only retain the latter, but
     * the former must remain valid while the same transaction reduces its
     * physical confirmation before the cargo event.
     */
    static void validateMaterialLoadingReceiptForRecovery(ExactInventory inventory, Map<SubjectId, RouteConstruction> projects,
                                                           Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents,
                                                           Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations,
                                                           PhysicalIntent intent, RouteConstructionMaterialLoadObservation observation) {
        if (intent.kind() != PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING || !intent.id().equals(observation.intentId())
                || !intent.causeSubjectId().equals(observation.projectId()) || intent.subjectIds().size() != 5
                || !intent.subjectIds().getFirst().equals(FrontierRouteNetwork.OWNER)
                || !intent.subjectIds().get(2).equals(observation.cargoId()) || !intent.subjectIds().get(3).equals(observation.cargoItemId())
                || !intent.subjectIds().get(4).equals(observation.sourceItemId())) {
            throw new IllegalArgumentException("route construction material recovery receipt has foreign identities");
        }
        RouteConstruction project = projects.get(observation.projectId()); CargoBatch cargo = inventory.cargo().get(observation.cargoId());
        if (project == null) throw new IllegalArgumentException("route construction material recovery receipt has no active project");
        if (project.cargoId().isEmpty() && cargo == null && matchesSource(inventory, observation, observation.sourceRemainingCount() + 1)) {
            // The same atomic transaction has confirmed physical removal but has not yet reduced its cargo event.
            return;
        }
        ExactItemStack cargoItem = inventory.items().get(observation.cargoItemId());
        if (project.cargoId().equals(java.util.Optional.of(observation.cargoId())) && cargo != null
                && cargo.ownerId().equals(FrontierRouteNetwork.OWNER) && cargo.itemIds().equals(java.util.List.of(observation.cargoItemId()))
                && cargoItem != null && cargoItem.count() == 1 && cargoItem.custody().equals(new InventoryCustody.Cargo(observation.cargoId()))
                && matchesSource(inventory, observation, observation.sourceRemainingCount())) {
            return;
        }
        boolean consumed = observations.values().stream().filter(RouteConstructionObservation.class::isInstance).map(RouteConstructionObservation.class::cast)
                .anyMatch(construction -> construction.projectId().equals(project.id()) && construction.itemId().equals(observation.cargoItemId())
                        && intents.get(construction.intentId()) != null && intents.get(construction.intentId()).status() == PhysicalIntentStatus.CONFIRMED);
        if (project.cargoId().isEmpty() && cargo == null && cargoItem == null && consumed
                && matchesSource(inventory, observation, observation.sourceRemainingCount())) return;
        throw new IllegalArgumentException("route construction material recovery receipt lacks its exact COLD cargo or confirmed consumption");
    }

    private static boolean matchesSource(ExactInventory inventory, RouteConstructionMaterialLoadObservation observation, int count) {
        ExactItemStack source = inventory.items().get(observation.sourceItemId());
        if (count == 0) return source == null;
        if (source == null || source.count() != count || !(source.custody() instanceof InventoryCustody.ContainerSlot slot)
                || !slot.containerId().equals(FrontierRouteNetwork.MAINTENANCE_CONTAINER)) return false;
        ContainerSurface surface = inventory.surfaces().get(slot.containerId());
        return surface != null && surface.status() == ContainerSurfaceStatus.ACTIVE;
    }

    static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, RouteConstructionObservation observation,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        validateIntent(state, intent);
        RouteConstruction project = state.routeConstructions().get(observation.projectId());
        if (project == null || !intent.subjectIds().contains(observation.projectId()) || !intent.subjectIds().contains(observation.itemId())
                || !observation.position().equals(nextCell(state, project))) throw new IllegalArgumentException("route construction receipt differs from active work");
        ExactItemStack material = state.inventory().items().get(observation.itemId());
        if (material == null) throw new IllegalArgumentException("route construction receipt lacks its COLD cargo item");
        Map<SubjectId, RouteConstruction> projects = new LinkedHashMap<>(state.routeConstructions());
        int confirmed = project.confirmedCells() + 1;
        int required = FrontierRouteNetwork.constructionCells(state.bootstrap(), state.routeTopology(), project.settlementId(), project.waypoints()).size();
        RouteConstruction completed = project.withConfirmedCells(confirmed, confirmed == required ? RouteConstructionStatus.READY : RouteConstructionStatus.BUILDING);
        projects.put(project.id(), material.count() == 1 ? completed.withoutCargo() : completed);
        intents.put(intent.id(), intent.withStatus(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(observation.id())));
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(observation.id(), observation);
        return new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(), state.infection(), state.inventory().consumeCargoUnit(project.cargoId().orElseThrow(), observation.itemId()),
                state.productionJobs(), state.contracts(), state.operations(), state.logisticsHistory(), intents, observations, state.sceneLeases(), state.hiveColony(),
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
                state.contracts(), state.operations(), state.logisticsHistory(), intents, state.physicalObservations(), state.sceneLeases(), state.hiveColony(), state.structureDamage(),
                state.physicalDeltas(), state.ambientLeases(), projects, state.routeTopology(), state.strategicPlans(), state.humanPopulation(), state.resourceSites());
    }

    static FrontierWorldState cutover(FrontierWorldState state, SubjectId projectId) {
        RouteConstruction project = state.routeConstructions().get(projectId);
        if (project == null || project.status() != RouteConstructionStatus.READY) throw new IllegalArgumentException("route topology cutover requires ready construction");
        List<BlockPosition> required = FrontierRouteNetwork.constructionCells(state.bootstrap(), state.routeTopology(), project.settlementId(), project.waypoints());
        if (project.confirmedCells() != required.size()) throw new IllegalArgumentException("route topology cutover has incomplete physical construction");
        Map<SubjectId, RouteConstruction> projects = new LinkedHashMap<>(state.routeConstructions()); projects.remove(projectId);
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents = new LinkedHashMap<>(state.physicalIntents());
        java.util.Set<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId> retired = intents.values().stream()
                .filter(intent -> intent.subjectIds().contains(projectId)).map(PhysicalIntent::id).collect(java.util.stream.Collectors.toSet());
        retired.forEach(intents::remove);
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.entrySet().removeIf(entry -> retired.contains(entry.getValue().intentId()));
        return new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(),
                state.contracts(), state.operations(), state.logisticsHistory(), intents, observations, state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), projects,
                state.routeTopology().replaceSupplyRoute(state.bootstrap(), project.settlementId(), project.waypoints()), state.strategicPlans(), state.humanPopulation(), state.resourceSites());
    }

    static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, RouteConstructionStarted started) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route construction must be owned by the route network");
        return begin(state, started.project());
    }
    static FrontierWorldState reduceMaterialLoaded(FrontierWorldState state, SubjectId subject, RouteConstructionMaterialLoaded loaded) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route construction material cargo must be owned by the route network");
        RouteConstruction project = state.routeConstructions().get(loaded.projectId());
        if (project == null || project.cargoId().isPresent() || !loaded.cargo().ownerId().equals(FrontierRouteNetwork.OWNER) || loaded.cargo().itemIds().size() != 1) {
            throw new IllegalArgumentException("route construction material cargo has invalid project ownership");
        }
        PhysicalIntent intent = state.physicalIntents().values().stream().filter(value -> value.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING)
                .filter(value -> value.causeSubjectId().equals(project.id())).findFirst().orElseThrow(() -> new IllegalArgumentException("route construction material cargo has no pickup intent"));
        if (intent.status() != PhysicalIntentStatus.CONFIRMED || intent.postconditionObservationId().isEmpty()) throw new IllegalArgumentException("route construction material cargo pickup is not confirmed");
        PhysicalEffectObservation evidence = state.physicalObservations().get(intent.postconditionObservationId().orElseThrow());
        if (!(evidence instanceof RouteConstructionMaterialLoadObservation observation)) throw new IllegalArgumentException("route construction material cargo has invalid pickup evidence");
        validateMaterialLoadingReceipt(state, intent, observation);
        if (!loaded.cargo().id().equals(observation.cargoId()) || !loaded.cargo().itemIds().equals(java.util.List.of(observation.cargoItemId()))) {
            throw new IllegalArgumentException("route construction material cargo differs from its observed pickup");
        }
        Map<SubjectId, RouteConstruction> projects = new LinkedHashMap<>(state.routeConstructions()); projects.put(project.id(), project.withCargo(loaded.cargo().id()));
        return new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(), state.infection(), state.inventory()
                .extractOneToCargo(observation.sourceItemId(), loaded.cargo(), observation.cargoItemId()),
                state.productionJobs(), state.contracts(), state.operations(), state.logisticsHistory(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), projects, state.routeTopology(), state.strategicPlans(), state.humanPopulation(), state.resourceSites());
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
