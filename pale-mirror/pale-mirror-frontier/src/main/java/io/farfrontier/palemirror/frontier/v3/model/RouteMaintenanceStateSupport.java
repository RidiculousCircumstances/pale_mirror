package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Bounded ownership and evidence invariants for in-place retained-route repair. */
public final class RouteMaintenanceStateSupport {
    public static final int MAX_MAINTENANCE = 12;

    private RouteMaintenanceStateSupport() { }

    public static boolean ownsIntent(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        return intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE
                || intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING;
    }

    static boolean ownsMaintenance(Map<SubjectId, RouteMaintenance> maintenances, SubjectId id) { return maintenances.containsKey(id); }

    static boolean reservesSubject(Map<SubjectId, RouteMaintenance> maintenances,
                                   io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent, SubjectId subject) {
        RouteMaintenance maintenance = maintenances.get(intent.causeSubjectId());
        return intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING
                && maintenance != null && maintenance.cargoId().isEmpty()
                && (subject.equals(maintenance.plannedCargoId()) || subject.equals(maintenance.plannedCargoItemId()));
    }

    static void validate(FrontierBootstrap bootstrap, HiveColony colony, RouteTopology topology, Map<SubjectId, RouteConstruction> constructions,
                         Map<SubjectId, RouteMaintenance> maintenances, Map<BlockPosition, PhysicalDelta> deltas,
                         Map<SubjectId, ActorLocation> actors, HumanPopulation population,
                         Map<SubjectId, ProductionJob> jobs, ResourceSiteState sites, Map<SubjectId, RouteOperation> operations,
                         Map<SubjectId, SupplyContract> contracts, StrategicPlanState plans) {
        if (maintenances.size() > MAX_MAINTENANCE) throw new IllegalArgumentException("route maintenance retention limit exceeded");
        Set<BlockPosition> repairedCells = new HashSet<>();
        Set<SubjectId> ids = new HashSet<>(constructions.keySet());
        for (Map.Entry<SubjectId, RouteMaintenance> entry : maintenances.entrySet()) {
            RouteMaintenance maintenance = entry.getValue();
            if (!entry.getKey().equals(maintenance.id()) || !ids.add(maintenance.id()) || !repairedCells.add(maintenance.repairCell())) {
                throw new IllegalArgumentException("route maintenance identity or repair cell is duplicated");
            }
            PhysicalDelta loss = deltas.get(maintenance.repairCell());
            if (maintenance.status() != RouteMaintenanceStatus.READY
                    && (loss == null || loss.kind() != PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS
                    || !loss.semanticTarget().filter(target -> target.kind() == PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK
                    && FrontierRouteNetwork.OWNER.equals(target.subjectId())).isPresent()
                    || !loss.semanticPart().equals(java.util.Optional.of(maintenance.semanticPart())))) {
                throw new IllegalArgumentException("active route maintenance must retain its exact route-loss evidence");
            }
            GrayboxCell expected = FrontierGrayboxPlan.intactSemanticCell(bootstrap, colony, topology, constructions,
                    FrontierRouteNetwork.OWNER, maintenance.repairCell());
            if (expected == null || expected.semanticPart() != maintenance.semanticPart()
                    || expected.material() != maintenance.expectedMaterial()) {
                throw new IllegalArgumentException("route maintenance repair cell no longer matches the retained route plan");
            }
            EngineeringWorksite.validate(bootstrap, topology, maintenance);
            // A semantic checkpoint is not the independently observed physical pose.
        }
        RouteConstructionTeamStateSupport.validate(population, constructions, maintenances, jobs, sites, operations, contracts, plans);
    }

    static void validateAmbientAssemblyLeases(Map<SubjectId, RouteMaintenance> maintenances,
                                              Map<SubjectId, AmbientActorLease> ambientLeases) {
        for (RouteMaintenance maintenance : maintenances.values()) for (Map.Entry<SubjectId, EngineeringWorkAssembly.Member> entry : maintenance.assembly()
                .map(EngineeringWorkAssembly::members).orElse(Map.of()).entrySet()) {
            AmbientActorLease lease = ambientLeases.get(entry.getKey());
            if (lease == null || lease.status() == AmbientLeaseStatus.CLOSED) continue;
            if (lease.goal() != AmbientGoalKind.ENGINEERING_ASSEMBLY) {
                throw new IllegalArgumentException("maintenance assembly has a foreign active scope");
            }
        }
    }

    static FrontierWorldState begin(FrontierWorldState state, RouteMaintenance maintenance,
                                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup executions) {
        if (state.routeMaintenances().containsKey(maintenance.id()) || state.routeConstructions().containsKey(maintenance.id())
                || state.routeMaintenances().values().stream().anyMatch(current -> current.repairCell().equals(maintenance.repairCell()))) {
            throw new IllegalArgumentException("route maintenance identity or repair cell is already active");
        }
        Map<SubjectId, RouteMaintenance> next = new LinkedHashMap<>(state.routeMaintenances()); next.put(maintenance.id(), maintenance);
        return EngineeringExecutionAuthority.admit(state, maintenance, Optional.of(executions), FrontierWorldStateUpdate.begin().routeMaintenances(next));
    }

    public static FrontierWorldState reduceStarted(FrontierWorldState state, SubjectId subject, RouteMaintenanceStarted started) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route maintenance must be owned by the route network");
        return begin(state, started.maintenance(), started.executions());
    }

    public static FrontierWorldState reduceMaterialLoaded(FrontierWorldState state, SubjectId subject, RouteMaintenanceMaterialLoaded loaded) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route maintenance cargo must be owned by the route network");
        RouteMaintenance maintenance = state.routeMaintenances().get(loaded.maintenanceId());
        if (maintenance == null || maintenance.cargoId().isPresent() || !loaded.cargo().ownerId().equals(FrontierRouteNetwork.OWNER)
                || loaded.cargo().itemIds().size() != 1) throw new IllegalArgumentException("route maintenance cargo has invalid ownership");
        io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent = state.physicalIntents().values().stream()
                .filter(value -> value.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING)
                .filter(value -> value.causeSubjectId().equals(maintenance.id())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("route maintenance cargo has no pickup intent"));
        if (intent.status() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED || intent.postconditionObservationId().isEmpty()) {
            throw new IllegalArgumentException("route maintenance cargo pickup is not confirmed");
        }
        PhysicalEffectObservation evidence = state.physicalObservations().get(intent.postconditionObservationId().orElseThrow());
        if (!(evidence instanceof RouteMaintenanceMaterialLoadObservation observation)) throw new IllegalArgumentException("route maintenance cargo has invalid pickup evidence");
        validateMaterialLoadingReceipt(state, intent, observation);
        if (!loaded.cargo().id().equals(observation.cargoId()) || !loaded.cargo().itemIds().equals(java.util.List.of(observation.cargoItemId()))) {
            throw new IllegalArgumentException("route maintenance cargo differs from its observed pickup");
        }
        Map<SubjectId, RouteMaintenance> next = new LinkedHashMap<>(state.routeMaintenances()); next.put(maintenance.id(), maintenance.withCargo(loaded.cargo().id()));
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().extractOneToCargo(observation.sourceItemId(), loaded.cargo(), observation.cargoItemId()))
                .routeMaintenances(next));
    }

    public static FrontierWorldState reduceAssemblyStarted(FrontierWorldState state, SubjectId subject, RouteMaintenanceAssemblyStarted started) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route maintenance assembly must be owned by the route network");
        RouteMaintenance maintenance = state.routeMaintenances().get(started.maintenanceId());
        if (maintenance == null || (maintenance.assembly().isPresent() && !(maintenance.assembly().orElseThrow().purpose() == EngineeringJourneyPurpose.MUSTER_DEPOT
                && maintenance.assembly().orElseThrow().complete() && started.assembly().purpose() == EngineeringJourneyPurpose.WORKSITE))) {
            throw new IllegalArgumentException("route maintenance has no unassembled operation");
        }
        EngineeringWorksite.validate(state.bootstrap(), state.routeTopology(), maintenance.withAssembly(started.assembly()));
        Map<SubjectId, RouteMaintenance> next = new LinkedHashMap<>(state.routeMaintenances()); next.put(maintenance.id(), maintenance.withAssembly(started.assembly()));
        return EngineeringExecutionAuthority.assembled(state, maintenance, started.assembly(), started.executions(), started.workExecutions(),
                FrontierWorldStateUpdate.begin().routeMaintenances(next));
    }

    public static FrontierWorldState reduceAssemblyAdvanced(FrontierWorldState state, SubjectId subject, RouteMaintenanceAssemblyAdvanced advanced) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route maintenance assembly must be owned by the route network");
        RouteMaintenance maintenance = state.routeMaintenances().get(advanced.maintenanceId());
        EngineeringWorkAssembly current = maintenance == null ? null : maintenance.assembly().orElse(null);
        if (current == null || !current.members().keySet().equals(advanced.assembly().members().keySet())) {
            throw new IllegalArgumentException("route maintenance has no matching current crew");
        }
        SubjectId moved = EngineeringAssemblyProgress.validate(state, maintenance, advanced.assembly(), advanced.executions(), advanced.hotArrival());
        var update = FrontierWorldStateUpdate.begin();
        if (advanced.hotArrival().isEmpty()) {
            Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
            ActorLocation prior = actors.get(moved);
            actors.put(moved, new ActorLocation(advanced.assembly().members().get(moved).currentSurface().standingBody(), prior.condition(), prior.kind()));
            update.actorLocations(actors);
        }
        Map<SubjectId, RouteMaintenance> next = new LinkedHashMap<>(state.routeMaintenances()); next.put(maintenance.id(), maintenance.withAdvancedAssembly(advanced.assembly()));
        Map<SubjectId, AmbientActorLease> ambient = new LinkedHashMap<>(state.ambientLeases());
        AmbientActorLease lease = ambient.get(moved);
        if (lease != null && lease.status() == AmbientLeaseStatus.HOT && lease.goal() == AmbientGoalKind.ENGINEERING_ASSEMBLY) {
            EngineeringWorkAssembly.Member member = advanced.assembly().members().get(moved);
            SurfaceAnchor target = member.arrived() ? member.currentSurface() : member.nextSurface();
            ambient.put(moved, lease.withGoal(AmbientGoalKind.ENGINEERING_ASSEMBLY, target.standingBody()));
        }
        return EngineeringExecutionAuthority.assembled(state, maintenance, advanced.assembly(), advanced.executions(), advanced.workExecutions(),
                update.routeMaintenances(next).ambientLeases(ambient));
    }

    public static FrontierWorldState reduceClosed(FrontierWorldState state, SubjectId subject, RouteMaintenanceClosed closed) {
        if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route maintenance close must be owned by the route network");
        RouteMaintenance maintenance = state.routeMaintenances().get(closed.maintenanceId());
        if (maintenance == null || maintenance.status() != RouteMaintenanceStatus.READY || maintenance.team().memberIds().stream()
                .anyMatch(member -> EngineeringToolCustody.holdsTool(state, member)) || state.physicalIntents().values().stream()
                .anyMatch(intent -> intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.EQUIPMENT_RETURN
                        && intent.status() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED
                        && intent.roles().require(PhysicalIntentSubjectRole.ROUTE_MAINTENANCE).equals(maintenance.id()))) {
            throw new IllegalArgumentException("route maintenance close requires a repaired and disarmed operation");
        }
        boolean retainedScene = state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isEngineeringWorksite)
                .anyMatch(lease -> FrontierSceneBehaviors.engineeringWorksite(lease).projectId().equals(maintenance.id())
                        && lease.status() != SceneLeaseStatus.CLOSED);
        if (retainedScene) throw new IllegalArgumentException("route maintenance close cannot discard a retained worksite lease");
        EngineeringExecutionAuthority.requireTerminal(state, maintenance, Optional.of(closed.executions()));
        Map<SubjectId, RouteMaintenance> maintenances = new LinkedHashMap<>(state.routeMaintenances()); maintenances.remove(maintenance.id());
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents = new LinkedHashMap<>(state.physicalIntents());
        Set<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId> retired = intents.values().stream().filter(intent -> ownsIntent(intent)
                        && intent.roles().require(PhysicalIntentSubjectRole.ROUTE_MAINTENANCE).equals(maintenance.id()))
                .map(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent::id).collect(java.util.stream.Collectors.toSet());
        retired.forEach(intents::remove);
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.entrySet().removeIf(entry -> retired.contains(entry.getValue().intentId()));
        Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases());
        leases.entrySet().removeIf(entry -> FrontierSceneBehaviors.isEngineeringWorksite(entry.getValue())
                && FrontierSceneBehaviors.engineeringWorksite(entry.getValue()).projectId().equals(maintenance.id())
                && entry.getValue().status() == SceneLeaseStatus.CLOSED);
        return state.withChanges(FrontierWorldStateUpdate.begin().routeMaintenances(maintenances).physicalIntents(intents)
                .physicalObservations(observations).sceneLeases(leases).actorExecutions(EngineeringExecutionAuthority.retired(state, maintenance)));
    }

    public static void validateWorkIntent(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE
                || !intent.causeSubjectId().equals(FrontierRouteNetwork.OWNER) || !intent.roles().require(PhysicalIntentSubjectRole.ROUTE).equals(FrontierRouteNetwork.OWNER)) {
            throw new IllegalArgumentException("route maintenance work intent has invalid route ownership");
        }
        RouteMaintenance maintenance = workOperation(state, intent);
        EngineeringExecutionAuthority.requireWork(state, maintenance);
        SubjectId cargoId = maintenance.cargoId().orElseThrow(() -> new IllegalArgumentException("route maintenance work intent has no cargo"));
        SubjectId itemId = intent.roles().require(PhysicalIntentSubjectRole.MATERIAL);
        ExactItemStack item = state.inventory().items().get(itemId); CargoBatch cargo = state.inventory().cargo().get(cargoId);
        PhysicalDelta loss = state.physicalDeltas().get(maintenance.repairCell());
        if (!maintenance.building() || cargo == null || !cargo.ownerId().equals(FrontierRouteNetwork.OWNER)
                || !cargo.itemIds().equals(java.util.List.of(itemId)) || !itemId.equals(maintenance.plannedCargoItemId())
                || item == null || !item.itemKind().equals(maintenance.expectedMaterial().repairItemKind())
                || !item.custody().equals(new InventoryCustody.Cargo(cargoId))
                || loss == null || loss.kind() != PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS
                || !loss.semanticTarget().filter(target -> target.kind() == PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK
                && FrontierRouteNetwork.OWNER.equals(target.subjectId())).isPresent()
                || !loss.semanticPart().equals(Optional.of(maintenance.semanticPart()))
                || !wholeBlock(intent).equals(maintenance.repairCell())) {
            throw new IllegalArgumentException("route maintenance work intent lacks exact loss/cargo preconditions");
        }
    }

    /**
     * Resolves the retained maintenance operation from the intent's exact subject binding.
     *
     * <p>The route network is the durable cause/semantic owner of an in-place repair, while the
     * operation is one of the bound subjects.  Physical adapters must use this relation rather
     * than treating {@code causeSubjectId} as an operation ID; otherwise a valid prepared intent
     * is silently unexecutable.</p>
     */
    public static RouteMaintenance workOperation(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE
                || !intent.causeSubjectId().equals(FrontierRouteNetwork.OWNER)
                || !intent.roles().require(PhysicalIntentSubjectRole.ROUTE).equals(FrontierRouteNetwork.OWNER)) {
            throw new IllegalArgumentException("route maintenance work intent has invalid route ownership");
        }
        return java.util.Optional.ofNullable(state.routeMaintenances().get(intent.roles().require(PhysicalIntentSubjectRole.ROUTE_MAINTENANCE))).filter(java.util.Objects::nonNull).orElseThrow(() -> new
                IllegalArgumentException("route maintenance work intent lacks an active operation"));
    }

    public static void validateMaterialLoadingIntent(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING
                || !intent.roles().require(PhysicalIntentSubjectRole.ROUTE).equals(FrontierRouteNetwork.OWNER)
                || !intent.causeSubjectId().equals(intent.roles().require(PhysicalIntentSubjectRole.ROUTE_MAINTENANCE))) {
            throw new IllegalArgumentException("route maintenance pickup has invalid ownership");
        }
        RouteMaintenance maintenance = state.routeMaintenances().get(intent.causeSubjectId());
        if (maintenance == null || !maintenance.building() || maintenance.cargoId().isPresent()) {
            throw new IllegalArgumentException("route maintenance pickup has no unsupplied active operation");
        }
        SubjectId cargo = intent.roles().require(PhysicalIntentSubjectRole.CARGO), cargoItem = intent.roles().require(PhysicalIntentSubjectRole.CARGO_ITEM), source = intent.roles().require(PhysicalIntentSubjectRole.SOURCE_ITEM);
        ExactItemStack item = state.inventory().items().get(source);
        if (!cargo.equals(maintenance.plannedCargoId()) || state.inventory().cargo().containsKey(cargo)
                || !cargoItem.equals(maintenance.plannedCargoItemId()) || state.inventory().items().containsKey(cargoItem)
                || item == null || item.count() < 1 || !item.itemKind().equals(maintenance.expectedMaterial().repairItemKind())
                || !(item.custody() instanceof InventoryCustody.ContainerSlot slot)
                || !slot.containerId().equals(FrontierRouteNetwork.MAINTENANCE_CONTAINER)
                || state.inventory().surfaces().get(slot.containerId()) == null
                || state.inventory().surfaces().get(slot.containerId()).status() != ContainerSurfaceStatus.ACTIVE
                || !wholeBlock(intent).equals(FrontierRouteNetwork.maintenanceContainerPosition(state.bootstrap()))) {
            throw new IllegalArgumentException("route maintenance pickup lacks an active exact maintenance stack");
        }
        boolean sourceAlreadyReserved = state.physicalIntents().values().stream().anyMatch(existing -> existing.kind()
                == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING
                && (existing.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.PREPARED
                || existing.status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.RUNNING)
                && !existing.id().equals(intent.id()) && existing.roles().require(PhysicalIntentSubjectRole.SOURCE_ITEM).equals(source));
        if (sourceAlreadyReserved) throw new IllegalArgumentException("route maintenance source stack is already reserved by another active pickup");
    }

    public static void validateMaterialLoadingReceipt(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                      RouteMaintenanceMaterialLoadObservation observation) {
        validateMaterialLoadingIntent(state, intent);
        ExactItemStack source = state.inventory().items().get(observation.sourceItemId());
        if (!intent.id().equals(observation.intentId()) || !intent.causeSubjectId().equals(observation.maintenanceId())
                || !intent.roles().require(PhysicalIntentSubjectRole.CARGO).equals(observation.cargoId()) || !intent.roles().require(PhysicalIntentSubjectRole.CARGO_ITEM).equals(observation.cargoItemId())
                || !intent.roles().require(PhysicalIntentSubjectRole.SOURCE_ITEM).equals(observation.sourceItemId()) || source == null
                || observation.sourceRemainingCount() != source.count() - 1) {
            throw new IllegalArgumentException("route maintenance pickup receipt differs from prepared intent");
        }
    }

    /** Validates either reducer boundary of the durable one-unit source-to-cargo transfer. */
    static void validateMaterialLoadingReceiptForRecovery(ExactInventory inventory, Map<SubjectId, RouteMaintenance> maintenances,
                                                           Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents,
                                                           Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations,
                                                           io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                           RouteMaintenanceMaterialLoadObservation observation) {
        if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING
                || !intent.id().equals(observation.intentId()) || !intent.causeSubjectId().equals(observation.maintenanceId())
                || !intent.roles().require(PhysicalIntentSubjectRole.ROUTE).equals(FrontierRouteNetwork.OWNER)
                || !intent.roles().require(PhysicalIntentSubjectRole.CARGO).equals(observation.cargoId()) || !intent.roles().require(PhysicalIntentSubjectRole.CARGO_ITEM).equals(observation.cargoItemId())
                || !intent.roles().require(PhysicalIntentSubjectRole.SOURCE_ITEM).equals(observation.sourceItemId())) {
            throw new IllegalArgumentException("route maintenance material recovery receipt has foreign identities");
        }
        RouteMaintenance maintenance = maintenances.get(observation.maintenanceId()); CargoBatch cargo = inventory.cargo().get(observation.cargoId());
        if (maintenance == null) throw new IllegalArgumentException("route maintenance material recovery receipt has no active operation");
        if (maintenance.cargoId().isEmpty() && cargo == null && matchesSource(inventory, observation, observation.sourceRemainingCount() + 1)) return;
        ExactItemStack cargoItem = inventory.items().get(observation.cargoItemId());
        if (maintenance.cargoId().equals(Optional.of(observation.cargoId())) && cargo != null && cargo.ownerId().equals(FrontierRouteNetwork.OWNER)
                && cargo.itemIds().equals(java.util.List.of(observation.cargoItemId())) && cargoItem != null && cargoItem.count() == 1
                && cargoItem.custody().equals(new InventoryCustody.Cargo(observation.cargoId()))) return;
        boolean consumed = observations.values().stream().filter(RouteMaintenanceObservation.class::isInstance).map(RouteMaintenanceObservation.class::cast)
                .anyMatch(repair -> repair.maintenanceId().equals(maintenance.id()) && repair.itemId().equals(observation.cargoItemId())
                        && intents.get(repair.intentId()) != null && intents.get(repair.intentId()).status() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED);
        if (maintenance.cargoId().isEmpty() && cargo == null && cargoItem == null && consumed) return;
        throw new IllegalArgumentException("route maintenance material recovery receipt lacks its exact COLD cargo or confirmed consumption");
    }

    static void validateReceipt(FrontierBootstrap bootstrap, RouteTopology topology, Map<SubjectId, RouteMaintenance> maintenances,
                                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent, RouteMaintenanceObservation observation) {
        if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE
                || !intent.roles().require(PhysicalIntentSubjectRole.ROUTE_MAINTENANCE).equals(observation.maintenanceId()) || !intent.roles().require(PhysicalIntentSubjectRole.MATERIAL).equals(observation.itemId())) {
            throw new IllegalArgumentException("route maintenance receipt has foreign subjects");
        }
        RouteMaintenance maintenance = maintenances.get(observation.maintenanceId());
        if (maintenance == null || !maintenance.repairCell().equals(observation.position())) {
            throw new IllegalArgumentException("route maintenance receipt is outside its retained repair cell");
        }
    }

    private static boolean matchesSource(ExactInventory inventory, RouteMaintenanceMaterialLoadObservation observation, int count) {
        ExactItemStack source = inventory.items().get(observation.sourceItemId());
        if (count == 0) return source == null;
        if (source == null || source.count() != count || !(source.custody() instanceof InventoryCustody.ContainerSlot slot)
                || !slot.containerId().equals(FrontierRouteNetwork.MAINTENANCE_CONTAINER)) return false;
        ContainerSurface surface = inventory.surfaces().get(slot.containerId());
        return surface != null && surface.status() == ContainerSurfaceStatus.ACTIVE;
    }

    static FrontierWorldState complete(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                       RouteMaintenanceObservation observation,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents) {
        validateWorkIntent(state, intent);
        RouteMaintenance maintenance = state.routeMaintenances().get(observation.maintenanceId());
        if (maintenance == null || !intent.roles().require(PhysicalIntentSubjectRole.MATERIAL).equals(observation.itemId()) || !observation.position().equals(maintenance.repairCell())) {
            throw new IllegalArgumentException("route maintenance receipt differs from active repair");
        }
        Map<SubjectId, RouteMaintenance> maintenances = new LinkedHashMap<>(state.routeMaintenances()); maintenances.put(maintenance.id(), maintenance.ready().withoutCargo());
        Map<BlockPosition, PhysicalDelta> deltas = new LinkedHashMap<>(state.physicalDeltas()); deltas.remove(maintenance.repairCell());
        Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases = FrontierEngineeringWorkSceneSupport.drainProjectWorksites(state, maintenance.id());
        intents.put(intent.id(), intent.withStatus(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, Optional.of(observation.id())));
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations()); observations.put(observation.id(), observation);
        return EngineeringExecutionAuthority.workSettled(state, maintenance, FrontierWorldStateUpdate.begin().inventory(state.inventory().consumeCargoUnit(maintenance.cargoId().orElseThrow(), observation.itemId()))
                .physicalIntents(intents).physicalObservations(observations).physicalDeltas(deltas).routeMaintenances(maintenances).sceneLeases(leases)
                .routeTopology(state.routeTopology().reconcileSupplyAvailability(state.bootstrap(), deltas)));
    }

    public static FrontierWorldState conflict(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents) {
        RouteMaintenance maintenance = java.util.Optional.ofNullable(state.routeMaintenances().get(intent.roles().require(PhysicalIntentSubjectRole.ROUTE_MAINTENANCE))).filter(java.util.Objects::nonNull).orElseThrow(()
                -> new IllegalArgumentException("route maintenance conflict has no active operation"));
        Map<SubjectId, RouteMaintenance> maintenances = new LinkedHashMap<>(state.routeMaintenances()); maintenances.put(maintenance.id(), maintenance.conflict());
        intents.put(intent.id(), intent.withRecoveryUnknown(PhysicalIntentRecoveryDiagnosticProducer.ENGINEERING_WORKSITE.stamp(intent)));
        return state.withChanges(FrontierWorldStateUpdate.begin().physicalIntents(intents).routeMaintenances(maintenances)
                .sceneLeases(FrontierEngineeringWorkSceneSupport.drainProjectWorksites(state, maintenance.id())));
    }

    public static FrontierWorldState confirm(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                      PhysicalEffectObservation evidence,
                                      Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents) {
        if (intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE) {
            if (!(evidence instanceof RouteMaintenanceObservation maintenance)) throw new IllegalArgumentException("route maintenance requires repair observation evidence");
            return complete(state, intent, maintenance, intents);
        }
        if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING
                || !(evidence instanceof RouteMaintenanceMaterialLoadObservation loading)) {
            throw new IllegalArgumentException("route maintenance material loading requires exact pickup evidence");
        }
        validateMaterialLoadingReceipt(state, intent, loading);
        intents.put(intent.id(), intent.withStatus(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, Optional.of(loading.id())));
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations());
        observations.put(loading.id(), loading);
        return state.withChanges(FrontierWorldStateUpdate.begin().physicalIntents(intents).physicalObservations(observations));
    }

    private static BlockPosition wholeBlock(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        long scale = io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE;
        if (intent.origin().x().raw() % scale != 0L || intent.origin().y().raw() % scale != 0L || intent.origin().z().raw() % scale != 0L) {
            throw new IllegalArgumentException("route maintenance origin must be a whole block position");
        }
        return new BlockPosition(Math.toIntExact(intent.origin().x().raw() / scale), Math.toIntExact(intent.origin().y().raw() / scale),
                Math.toIntExact(intent.origin().z().raw() / scale));
    }
}
