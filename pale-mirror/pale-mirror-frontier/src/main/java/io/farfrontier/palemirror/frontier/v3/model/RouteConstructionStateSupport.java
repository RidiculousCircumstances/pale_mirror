package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole;
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
                         StrategicPlanState plans, Map<SubjectId, AmbientActorLease> ambientLeases) {
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
                if (actor == null || !actor.supportingSurface().support().equals(position)) {
                    throw new IllegalArgumentException("engineering assembly must retain each member's exact canonical position");
                }
            }));
            project.assembly().ifPresent(assembly -> assembly.members().forEach((memberId, cursor) -> {
                AmbientActorLease lease = ambientLeases.get(memberId);
                if (lease == null || lease.status() == AmbientLeaseStatus.CLOSED) return;
                BlockPosition expected = cursor.arrived() ? cursor.currentPosition() : cursor.corridor().get(cursor.cursor() + 1);
                if (lease.goal() != AmbientGoalKind.ENGINEERING_ASSEMBLY
                        || !lease.goalBody().supportingSurface().support().equals(expected)) {
                    throw new IllegalArgumentException("active engineering assembly lease must retain its one exact next cursor");
                }
            }));
        }
        RouteConstructionTeamStateSupport.validate(population, constructions, Map.of(), jobs, sites, operations, contracts, plans);
    }

    static FrontierWorldState begin(FrontierWorldState state, RouteConstruction project,
                                    java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup> executions) {
        if (state.routeConstructions().containsKey(project.id()) || state.routeConstructions().values().stream()
                .anyMatch(current -> current.settlementId().equals(project.settlementId()))) {
            throw new IllegalArgumentException("route construction is already active for this identity or settlement");
        }
        RouteConstruction admitted = project.workCells().isEmpty() ? project.withWorkCells(FrontierRouteNetwork.constructionCells(
                state.bootstrap(), state.routeTopology(), project.settlementId(), project.waypoints())) : project;
        if (admitted.workCells().isEmpty()) throw new IllegalArgumentException("route construction has no immutable work plan");
        Map<SubjectId, RouteConstruction> next = new LinkedHashMap<>(state.routeConstructions()); next.put(admitted.id(), admitted);
        return EngineeringExecutionAuthority.admit(state, admitted, executions, FrontierWorldStateUpdate.begin().routeConstructions(next));
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
        RouteConstruction project = java.util.Optional.ofNullable(state.routeConstructions()
                .get(intent.roles().require(PhysicalIntentSubjectRole.ROUTE_CONSTRUCTION_PROJECT)))
                .filter(java.util.Objects::nonNull)
                .orElseThrow(() -> new IllegalArgumentException("route construction intent lacks an active project"));
        SubjectId cargoId = project.cargoId().orElseThrow(() -> new IllegalArgumentException("route construction intent has no loaded material cargo"));
        EngineeringExecutionAuthority.requireWork(state, project);
        if (!intent.roles().require(PhysicalIntentSubjectRole.CARGO).equals(cargoId)) throw new IllegalArgumentException("route construction intent lacks its material cargo");
        SubjectId materialId = intent.roles().require(PhysicalIntentSubjectRole.MATERIAL);
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
        if (intent.kind() != PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING || !intent.roles().require(PhysicalIntentSubjectRole.ROUTE).equals(FrontierRouteNetwork.OWNER)
                || !intent.causeSubjectId().equals(intent.roles().require(PhysicalIntentSubjectRole.ROUTE_CONSTRUCTION_PROJECT))) {
            throw new IllegalArgumentException("route construction material loading has invalid ownership");
        }
        RouteConstruction project = state.routeConstructions().get(intent.causeSubjectId());
        if (project == null || project.status() != RouteConstructionStatus.BUILDING || project.cargoId().isPresent()) {
            throw new IllegalArgumentException("route construction material loading has no unassigned active project");
        }
        SubjectId cargoId = intent.roles().require(PhysicalIntentSubjectRole.CARGO), cargoItemId = intent.roles().require(PhysicalIntentSubjectRole.CARGO_ITEM), itemId = intent.roles().require(PhysicalIntentSubjectRole.SOURCE_ITEM);
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
                || !intent.roles().require(PhysicalIntentSubjectRole.CARGO).equals(observation.cargoId()) || !intent.roles().require(PhysicalIntentSubjectRole.CARGO_ITEM).equals(observation.cargoItemId())
                || item == null || !item.id().equals(intent.roles().require(PhysicalIntentSubjectRole.SOURCE_ITEM))
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
                || !intent.causeSubjectId().equals(observation.projectId())
                || !intent.roles().require(PhysicalIntentSubjectRole.ROUTE).equals(FrontierRouteNetwork.OWNER)
                || !intent.roles().require(PhysicalIntentSubjectRole.CARGO).equals(observation.cargoId()) || !intent.roles().require(PhysicalIntentSubjectRole.CARGO_ITEM).equals(observation.cargoItemId())
                || !intent.roles().require(PhysicalIntentSubjectRole.SOURCE_ITEM).equals(observation.sourceItemId())) {
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

    public static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, RouteConstructionObservation observation,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        validateIntent(state, intent);
        RouteConstruction project = state.routeConstructions().get(observation.projectId());
        if (project == null || !intent.roles().require(PhysicalIntentSubjectRole.ROUTE_CONSTRUCTION_PROJECT).equals(observation.projectId()) || !intent.roles().require(PhysicalIntentSubjectRole.MATERIAL).equals(observation.itemId())
                || !observation.position().equals(nextCell(state, project))) throw new IllegalArgumentException("route construction receipt differs from active work");
        ExactItemStack material = state.inventory().items().get(observation.itemId());
        if (material == null) throw new IllegalArgumentException("route construction receipt lacks its COLD cargo item");
        Map<SubjectId, RouteConstruction> projects = new LinkedHashMap<>(state.routeConstructions());
        int confirmed = project.confirmedCells() + 1;
        int required = project.workCells().size();
        RouteConstruction completed = project.withConfirmedCells(confirmed, confirmed == required ? RouteConstructionStatus.READY : RouteConstructionStatus.BUILDING);
        projects.put(project.id(), material.count() == 1 ? completed.withoutCargo() : completed);
        Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases = FrontierEngineeringWorkSceneSupport.drainProjectWorksites(state, project.id());
        intents.put(intent.id(), intent.withStatus(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(observation.id())));
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(observation.id(), observation);
        return EngineeringExecutionAuthority.workSettled(state, project, FrontierWorldStateUpdate.begin().inventory(state.inventory().consumeCargoUnit(project.cargoId().orElseThrow(), observation.itemId()))
                .physicalIntents(intents).physicalObservations(observations).routeConstructions(projects).sceneLeases(leases));
    }

    public static FrontierWorldState conflict(FrontierWorldState state, PhysicalIntent intent,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents) {
        RouteConstruction project = java.util.Optional.ofNullable(state.routeConstructions()
                .get(intent.roles().require(PhysicalIntentSubjectRole.ROUTE_CONSTRUCTION_PROJECT)))
                .filter(java.util.Objects::nonNull)
                .orElseThrow(() -> new IllegalArgumentException("route construction conflict lacks its project"));
        Map<SubjectId, RouteConstruction> projects = new LinkedHashMap<>(state.routeConstructions());
        projects.put(project.id(), project.withConfirmedCells(project.confirmedCells(), RouteConstructionStatus.CONFLICT));
        intents.put(intent.id(), intent.withRecoveryUnknown(PhysicalIntentRecoveryDiagnosticProducer.ENGINEERING_WORKSITE.stamp(intent)));
        return state.withChanges(FrontierWorldStateUpdate.begin().physicalIntents(intents).routeConstructions(projects)
                .sceneLeases(FrontierEngineeringWorkSceneSupport.drainProjectWorksites(state, project.id())));
    }

    static FrontierWorldState cutover(FrontierWorldState state, SubjectId projectId) {
        RouteConstruction project = state.routeConstructions().get(projectId);
        if (project == null || project.status() != RouteConstructionStatus.READY) throw new IllegalArgumentException("route topology cutover requires ready construction");
        if (project.confirmedCells() != project.workCells().size()) throw new IllegalArgumentException("route topology cutover has incomplete physical construction");
        if (!readyToCutover(state, project))
            throw new IllegalArgumentException("route topology cutover cannot discard tools, unsettled effects or retained worksite leases");
        Map<SubjectId, RouteConstruction> projects = new LinkedHashMap<>(state.routeConstructions()); projects.remove(projectId);
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> intents = new LinkedHashMap<>(state.physicalIntents());
        java.util.Set<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId> retired = intents.values().stream()
                .filter(intent -> ownsIntent(intent, projectId)).map(PhysicalIntent::id).collect(java.util.stream.Collectors.toSet());
        retired.forEach(intents::remove);
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.entrySet().removeIf(entry -> retired.contains(entry.getValue().intentId()));
        Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases());
        leases.entrySet().removeIf(entry -> FrontierSceneBehaviors.isEngineeringWorksite(entry.getValue())
                && FrontierSceneBehaviors.engineeringWorksite(entry.getValue()).projectId().equals(projectId)
                && entry.getValue().status() == SceneLeaseStatus.CLOSED);
        return state.withChanges(FrontierWorldStateUpdate.begin().physicalIntents(intents).physicalObservations(observations)
                .sceneLeases(leases)
                .routeConstructions(projects).routeTopology(state.routeTopology().replaceSupplyRoute(state.bootstrap(), project.settlementId(), project.waypoints()))
                .actorExecutions(EngineeringExecutionAuthority.retired(state, project)));
    }

    public static boolean readyToCutover(FrontierWorldState state, RouteConstruction project) {
        return project.status() == RouteConstructionStatus.READY
                && project.engineeringTeam().stream().flatMap(team -> team.memberIds().stream())
                .noneMatch(member -> EngineeringToolCustody.holdsTool(state, member))
                && state.physicalIntents().values().stream().noneMatch(intent -> ownsIntent(intent, project.id())
                && intent.status() != PhysicalIntentStatus.CONFIRMED)
                && state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isEngineeringWorksite)
                .noneMatch(lease -> FrontierSceneBehaviors.engineeringWorksite(lease).projectId().equals(project.id())
                        && lease.status() != SceneLeaseStatus.CLOSED);
    }

    private static boolean ownsIntent(PhysicalIntent intent, SubjectId projectId) {
        return switch (intent.roles().schema()) {
            case ROUTE_CONSTRUCTION, ROUTE_CONSTRUCTION_LOADING ->
                    intent.roles().require(PhysicalIntentSubjectRole.ROUTE_CONSTRUCTION_PROJECT).equals(projectId);
            case ENGINEERING_EQUIPMENT_ISSUE, ENGINEERING_EQUIPMENT_RETURN ->
                    intent.roles().require(PhysicalIntentSubjectRole.ENGINEERING_WORK_ORDER).equals(projectId);
            default -> false;
        };
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, RouteConstructionStarted started) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route construction must be owned by the route network");
        return begin(state, started.project(), started.executions());
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
        if (project == null || (project.assembly().isPresent() && !(project.assembly().orElseThrow().purpose() == EngineeringJourneyPurpose.MUSTER_DEPOT
                && project.assembly().orElseThrow().complete() && started.assembly().purpose() == EngineeringJourneyPurpose.WORKSITE))) {
            throw new IllegalArgumentException("route construction assembly has no unassembled project");
        }
        EngineeringWorksite.validate(state.bootstrap(), state.routeTopology(), project.withAssembly(started.assembly()));
        Map<SubjectId, RouteConstruction> projects = new LinkedHashMap<>(state.routeConstructions());
        projects.put(project.id(), project.withAssembly(started.assembly()));
        return EngineeringExecutionAuthority.assembled(state, project, started.assembly(), started.executions(), started.workExecutions(),
                FrontierWorldStateUpdate.begin().routeConstructions(projects));
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
        AmbientActorLease authority = state.ambientLeases().get(moved);
        if ((authority == null || authority.status() == AmbientLeaseStatus.CLOSED) && !ActorExecutionCoordinator.coldAvailable(state, moved))
            throw new IllegalArgumentException("COLD engineering assembly cannot advance its physically held member");
        if (authority != null && authority.status() != AmbientLeaseStatus.CLOSED
                && (authority.status() != AmbientLeaseStatus.HOT || authority.goal() != AmbientGoalKind.ENGINEERING_ASSEMBLY))
            throw new IllegalArgumentException("engineering arrival has no exact HOT assembly authority");
        actors.put(moved, new ActorLocation(BodyPosition.above(new SurfaceAnchor(advanced.assembly().members().get(moved).currentPosition())), prior.condition(), prior.kind()));
        Map<SubjectId, RouteConstruction> projects = new LinkedHashMap<>(state.routeConstructions());
        projects.put(project.id(), project.withAdvancedAssembly(advanced.assembly()));
        Map<SubjectId, AmbientActorLease> ambient = new LinkedHashMap<>(state.ambientLeases());
        AmbientActorLease lease = ambient.get(moved);
        if (lease != null && lease.status() == AmbientLeaseStatus.HOT && lease.goal() == AmbientGoalKind.ENGINEERING_ASSEMBLY) {
            EngineeringWorkAssembly.Member member = advanced.assembly().members().get(moved);
            BlockPosition target = member.arrived() ? member.currentPosition() : member.corridor().get(member.cursor() + 1);
            ambient.put(moved, lease.withGoal(AmbientGoalKind.ENGINEERING_ASSEMBLY, BodyPosition.above(new SurfaceAnchor(target))));
        }
        return EngineeringExecutionAuthority.assembled(state, project, advanced.assembly(), advanced.executions(), advanced.workExecutions(),
                FrontierWorldStateUpdate.begin().actorLocations(actors).routeConstructions(projects).ambientLeases(ambient));
    }

    public static FrontierWorldState reduceCutover(FrontierWorldState state, SubjectId subject, RouteTopologyCutover cutover) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route topology cutover must be owned by the route network");
        EngineeringExecutionAuthority.requireTerminal(state, state.routeConstructions().get(cutover.projectId()), cutover.executions());
        return cutover(state, cutover.projectId());
    }

    static void validateReceipt(FrontierBootstrap bootstrap, RouteTopology topology, Map<SubjectId, RouteConstruction> projects,
                                PhysicalIntent intent, RouteConstructionObservation observation) {
        if (intent.kind() != PhysicalIntentKind.ROUTE_CONSTRUCTION || !intent.roles().require(PhysicalIntentSubjectRole.ROUTE_CONSTRUCTION_PROJECT).equals(observation.projectId())
                || !intent.roles().require(PhysicalIntentSubjectRole.MATERIAL).equals(observation.itemId())) throw new IllegalArgumentException("route construction receipt has foreign subjects");
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
