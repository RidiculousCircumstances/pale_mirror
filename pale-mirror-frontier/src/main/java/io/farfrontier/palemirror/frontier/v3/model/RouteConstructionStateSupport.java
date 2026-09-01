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
public final class RouteConstructionStateSupport {
    public static final int MAX_CONSTRUCTIONS = 12;
    private RouteConstructionStateSupport() { }

    static void validate(FrontierBootstrap bootstrap, RouteTopology topology, Map<SubjectId, RouteConstruction> constructions,
                         Map<SubjectId, ActorLocation> actors,
                         HumanPopulation population, Map<SubjectId, ProductionJob> jobs, ResourceSiteState sites,
                         Map<SubjectId, RouteOperation> operations, Map<SubjectId, SupplyContract> contracts,
                         StrategicPlanState plans) {
        if (constructions.size() > MAX_CONSTRUCTIONS) throw new IllegalArgumentException("route construction retention limit exceeded");
        HashSet<SubjectId> settlements = new HashSet<>();
        for (Map.Entry<SubjectId, RouteConstruction> entry : constructions.entrySet()) {
            RouteConstruction project = entry.getValue();
            if (!entry.getKey().equals(project.id()) || !settlements.add(project.settlementId())) {
                throw new IllegalArgumentException("route construction identity or settlement is duplicated");
            }
            FrontierRouteNetwork.validateSupplyWaypoints(bootstrap, project.settlementId(), project.waypoints());
            List<BlockPosition> required = project.workCells();
            if (required.isEmpty() || project.confirmedCells() > required.size()) throw new IllegalArgumentException("route construction cursor is invalid");
            if (project.status() == RouteConstructionStatus.READY != (project.confirmedCells() == required.size())) {
                throw new IllegalArgumentException("route construction readiness does not match confirmed work");
            }
            EngineeringWorksite.validate(bootstrap, topology, project);
            project.assembly().ifPresent(assembly -> assembly.positions().forEach((member, position) -> {
                ActorLocation actor = actors.get(member);
                if (actor == null || !actor.position().equals(position)) {
                    throw new IllegalArgumentException("engineering assembly must retain each member's exact canonical position");
                }
            }));
        }
        RouteConstructionTeamStateSupport.validate(population, constructions, jobs, sites, operations, contracts, plans);
    }

    static FrontierWorldState begin(FrontierWorldState state, RouteConstruction project) {
        if (state.routeConstructions().containsKey(project.id()) || state.routeConstructions().values().stream()
                .anyMatch(current -> current.settlementId().equals(project.settlementId()))) {
            throw new IllegalArgumentException("route construction is already active for this identity or settlement");
        }
        RouteConstruction admitted = project.workCells().isEmpty() ? project.withWorkCells(FrontierRouteNetwork.constructionCells(
                state.bootstrap(), state.routeTopology(), project.settlementId(), project.waypoints())) : project;
        if (admitted.workCells().isEmpty()) throw new IllegalArgumentException("route construction has no immutable work plan");
        Map<SubjectId, RouteConstruction> next = new LinkedHashMap<>(state.routeConstructions()); next.put(admitted.id(), admitted);
        return state.withChanges(FrontierWorldStateUpdate.begin().routeConstructions(next));
    }

    /** Legacy hydration derives absent pre-schema-86 work plans once; current snapshots retain theirs verbatim. */
    public static Map<SubjectId, RouteConstruction> hydrateWorkCells(FrontierBootstrap bootstrap, RouteTopology topology,
                                                                      Map<SubjectId, RouteConstruction> projects) {
        Map<SubjectId, RouteConstruction> hydrated = new LinkedHashMap<>();
        for (Map.Entry<SubjectId, RouteConstruction> entry : projects.entrySet()) {
            RouteConstruction project = entry.getValue();
            RouteConstruction exact = project.workCells().isEmpty() ? project.withWorkCells(FrontierRouteNetwork.constructionCells(
                    bootstrap, topology, project.settlementId(), project.waypoints())) : project;
            hydrated.put(entry.getKey(), exact);
        }
        return Map.copyOf(hydrated);
    }

    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
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
                || !materialId.equals(project.plannedCargoItemId())
                || !state.inventory().cargo().get(cargoId).itemIds().equals(java.util.List.of(materialId))) {
            throw new IllegalArgumentException("route construction intent lacks exact COLD work cargo");
        }
        if (!wholeBlock(intent).equals(nextCell(state, project))) throw new IllegalArgumentException("route construction intent does not target its next cell");
    }

    public static void validateMaterialLoadingIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING || !intent.subjectIds().contains(FrontierRouteNetwork.OWNER)
                || intent.subjectIds().size() != 5 || !intent.causeSubjectId().equals(intent.subjectIds().get(1))) {
            throw new IllegalArgumentException("route construction material loading has invalid ownership");
        }
        RouteConstruction project = state.routeConstructions().get(intent.causeSubjectId());
        if (project == null || project.status() != RouteConstructionStatus.BUILDING || project.cargoId().isPresent()) {
            throw new IllegalArgumentException("route construction material loading has no unassigned active project");
        }
        SubjectId cargoId = intent.subjectIds().get(2), cargoItemId = intent.subjectIds().get(3), itemId = intent.subjectIds().get(4);
        if (!cargoId.equals(project.plannedCargoId()) || state.inventory().cargo().containsKey(cargoId)
                || !cargoItemId.equals(project.plannedCargoItemId()) || state.inventory().items().containsKey(cargoItemId)) {
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

    public static void validateMaterialLoadingReceipt(FrontierWorldState state, PhysicalIntent intent, RouteConstructionMaterialLoadObservation observation) {
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
                && cargoItem != null && cargoItem.count() == 1 && cargoItem.custody().equals(new InventoryCustody.Cargo(observation.cargoId()))) {
            // Once the exact cargo exists, the source-stack count/custody is historical
            // evidence only. A later legitimate withdrawal or consumption of its remainder
            // must not make this already-confirmed cargo hand-off unrecoverable.
            return;
        }
        boolean consumed = observations.values().stream().filter(RouteConstructionObservation.class::isInstance).map(RouteConstructionObservation.class::cast)
                .anyMatch(construction -> construction.projectId().equals(project.id()) && construction.itemId().equals(observation.cargoItemId())
                        && intents.get(construction.intentId()) != null && intents.get(construction.intentId()).status() == PhysicalIntentStatus.CONFIRMED);
        if (project.cargoId().isEmpty() && cargo == null && cargoItem == null && consumed) return;
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
        int required = project.workCells().size();
        RouteConstruction completed = project.withConfirmedCells(confirmed, confirmed == required ? RouteConstructionStatus.READY : RouteConstructionStatus.BUILDING);
        projects.put(project.id(), material.count() == 1 ? completed.withoutCargo() : completed);
        Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases());
        leases.replaceAll((leaseId, lease) -> FrontierSceneBehaviors.isEngineeringWorksite(lease)
                && FrontierSceneBehaviors.engineeringWorksite(lease).projectId().equals(project.id())
                && FrontierSceneBehaviors.engineeringWorksite(lease).workCellIndex() == project.confirmedCells()
                && lease.status() == SceneLeaseStatus.HOT ? lease.withStatus(SceneLeaseStatus.DRAINING) : lease);
        intents.put(intent.id(), intent.withStatus(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(observation.id())));
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(observation.id(), observation);
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().consumeCargoUnit(project.cargoId().orElseThrow(), observation.itemId()))
                .physicalIntents(intents).physicalObservations(observations).routeConstructions(projects).sceneLeases(leases));
    }

    static FrontierWorldState conflict(FrontierWorldState state, PhysicalIntent intent,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        RouteConstruction project = intent.subjectIds().stream().map(state.routeConstructions()::get).filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("route construction conflict lacks its project"));
        Map<SubjectId, RouteConstruction> projects = new LinkedHashMap<>(state.routeConstructions());
        projects.put(project.id(), project.withConfirmedCells(project.confirmedCells(), RouteConstructionStatus.CONFLICT));
        intents.put(intent.id(), intent.withStatus(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, java.util.Optional.empty()));
        return state.withChanges(FrontierWorldStateUpdate.begin().physicalIntents(intents).routeConstructions(projects));
    }

    static FrontierWorldState cutover(FrontierWorldState state, SubjectId projectId) {
        RouteConstruction project = state.routeConstructions().get(projectId);
        if (project == null || project.status() != RouteConstructionStatus.READY) throw new IllegalArgumentException("route topology cutover requires ready construction");
        if (project.confirmedCells() != project.workCells().size()) throw new IllegalArgumentException("route topology cutover has incomplete physical construction");
        Map<SubjectId, RouteConstruction> projects = new LinkedHashMap<>(state.routeConstructions()); projects.remove(projectId);
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents = new LinkedHashMap<>(state.physicalIntents());
        java.util.Set<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId> retired = intents.values().stream()
                .filter(intent -> intent.subjectIds().contains(projectId)).map(PhysicalIntent::id).collect(java.util.stream.Collectors.toSet());
        retired.forEach(intents::remove);
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.entrySet().removeIf(entry -> retired.contains(entry.getValue().intentId()));
        return state.withChanges(FrontierWorldStateUpdate.begin().physicalIntents(intents).physicalObservations(observations)
                .routeConstructions(projects).routeTopology(state.routeTopology().replaceSupplyRoute(state.bootstrap(), project.settlementId(), project.waypoints())));
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, RouteConstructionStarted started) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route construction must be owned by the route network");
        return begin(state, started.project());
    }
    public static FrontierWorldState reduceMaterialLoaded(FrontierWorldState state, SubjectId subject, RouteConstructionMaterialLoaded loaded) {
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
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory()
                .extractOneToCargo(observation.sourceItemId(), loaded.cargo(), observation.cargoItemId())).routeConstructions(projects));
    }

    public static FrontierWorldState reduceAssemblyStarted(FrontierWorldState state, SubjectId subject, RouteConstructionAssemblyStarted started) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route construction assembly must be owned by the route network");
        RouteConstruction project = state.routeConstructions().get(started.projectId());
        if (project == null || project.assembly().isPresent()) throw new IllegalArgumentException("route construction assembly has no unassembled project");
        EngineeringWorksite.validate(state.bootstrap(), state.routeTopology(), project.withAssembly(started.assembly()));
        Map<SubjectId, RouteConstruction> projects = new LinkedHashMap<>(state.routeConstructions());
        projects.put(project.id(), project.withAssembly(started.assembly()));
        return state.withChanges(FrontierWorldStateUpdate.begin().routeConstructions(projects));
    }

    public static FrontierWorldState reduceAssemblyAdvanced(FrontierWorldState state, SubjectId subject, RouteConstructionAssemblyAdvanced advanced) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route construction assembly must be owned by the route network");
        RouteConstruction project = state.routeConstructions().get(advanced.projectId());
        EngineeringWorkAssembly current = project == null ? null : project.assembly().orElse(null);
        if (current == null || !current.members().keySet().equals(advanced.assembly().members().keySet())) {
            throw new IllegalArgumentException("route construction assembly has no matching current crew");
        }
        SubjectId moved = current.members().keySet().stream().filter(member -> current.members().get(member).cursor() != advanced.assembly().members().get(member).cursor())
                .reduce((left, right) -> { throw new IllegalArgumentException("engineering assembly advances more than one member"); }).orElseThrow(() ->
                        new IllegalArgumentException("engineering assembly advances no member"));
        if (!current.advance(moved).equals(advanced.assembly())) throw new IllegalArgumentException("engineering assembly advances outside its exact corridor");
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        ActorLocation prior = actors.get(moved);
        if (prior == null || prior.condition().status() != ActorLifeStatus.ALIVE) throw new IllegalArgumentException("engineering assembly advances a nonliving member");
        actors.put(moved, new ActorLocation(advanced.assembly().members().get(moved).currentPosition(), prior.condition()));
        Map<SubjectId, RouteConstruction> projects = new LinkedHashMap<>(state.routeConstructions());
        projects.put(project.id(), project.withAdvancedAssembly(advanced.assembly()));
        Map<SubjectId, AmbientActorLease> ambient = new LinkedHashMap<>(state.ambientLeases());
        AmbientActorLease lease = ambient.get(moved);
        if (lease != null && lease.status() == AmbientLeaseStatus.HOT && lease.goal() == AmbientGoalKind.ENGINEERING_ASSEMBLY) {
            EngineeringWorkAssembly.Member member = advanced.assembly().members().get(moved);
            BlockPosition target = member.arrived() ? member.currentPosition() : member.corridor().get(member.cursor() + 1);
            ambient.put(moved, lease.withGoal(AmbientGoalKind.ENGINEERING_ASSEMBLY, target));
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).routeConstructions(projects).ambientLeases(ambient));
    }

    public static FrontierWorldState reduceCutover(FrontierWorldState state, SubjectId subject, RouteTopologyCutover cutover) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route topology cutover must be owned by the route network");
        return cutover(state, cutover.projectId());
    }

    static void validateReceipt(FrontierBootstrap bootstrap, RouteTopology topology, Map<SubjectId, RouteConstruction> projects,
                                PhysicalIntent intent, RouteConstructionObservation observation) {
        if (intent.kind() != PhysicalIntentKind.ROUTE_CONSTRUCTION || !intent.subjectIds().contains(observation.projectId())
                || !intent.subjectIds().contains(observation.itemId())) throw new IllegalArgumentException("route construction receipt has foreign subjects");
        RouteConstruction project = projects.get(observation.projectId());
        if (project == null || !project.workCells().contains(observation.position())) throw new IllegalArgumentException("route construction receipt is outside its replacement corridor");
    }

    private static BlockPosition nextCell(FrontierWorldState state, RouteConstruction project) {
        return project.workCells().get(project.confirmedCells());
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
