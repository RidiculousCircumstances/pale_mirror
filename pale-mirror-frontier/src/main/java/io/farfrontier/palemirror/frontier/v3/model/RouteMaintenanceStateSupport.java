package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Bounded ownership and evidence invariants for in-place retained-route repair. */
public final class RouteMaintenanceStateSupport {
    public static final int MAX_MAINTENANCE = 12;

    private RouteMaintenanceStateSupport() { }

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
                    || !loss.ownerId().equals(java.util.Optional.of(FrontierRouteNetwork.OWNER))
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
            maintenance.assembly().ifPresent(assembly -> assembly.positions().forEach((member, position) -> {
                ActorLocation actor = actors.get(member);
                if (actor == null || !actor.supportingSurface().support().equals(position)) {
                    throw new IllegalArgumentException("route maintenance assembly must retain each member's exact canonical position");
                }
            }));
        }
        RouteConstructionTeamStateSupport.validate(population, constructions, maintenances, jobs, sites, operations, contracts, plans);
    }

    static void validateAmbientAssemblyLeases(Map<SubjectId, RouteMaintenance> maintenances,
                                              Map<SubjectId, AmbientActorLease> ambientLeases) {
        for (RouteMaintenance maintenance : maintenances.values()) for (Map.Entry<SubjectId, EngineeringWorkAssembly.Member> entry : maintenance.assembly()
                .map(EngineeringWorkAssembly::members).orElse(Map.of()).entrySet()) {
            EngineeringWorkAssembly.Member member = entry.getValue(); AmbientActorLease lease = ambientLeases.get(entry.getKey());
            if (lease == null || lease.status() == AmbientLeaseStatus.CLOSED) continue;
            BlockPosition expected = member.arrived() ? member.currentPosition() : member.corridor().get(member.cursor() + 1);
            if (lease.goal() != AmbientGoalKind.ENGINEERING_ASSEMBLY || !lease.goalBody().supportingSurface().support().equals(expected)) {
                throw new IllegalArgumentException("active route maintenance assembly lease must retain its one exact next cursor");
            }
        }
    }

    static FrontierWorldState begin(FrontierWorldState state, RouteMaintenance maintenance) {
        if (state.routeMaintenances().containsKey(maintenance.id()) || state.routeConstructions().containsKey(maintenance.id())
                || state.routeMaintenances().values().stream().anyMatch(current -> current.repairCell().equals(maintenance.repairCell()))) {
            throw new IllegalArgumentException("route maintenance identity or repair cell is already active");
        }
        Map<SubjectId, RouteMaintenance> next = new LinkedHashMap<>(state.routeMaintenances()); next.put(maintenance.id(), maintenance);
        return state.withChanges(FrontierWorldStateUpdate.begin().routeMaintenances(next));
    }

    public static void validateWorkIntent(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE
                || !intent.causeSubjectId().equals(FrontierRouteNetwork.OWNER) || !intent.subjectIds().contains(FrontierRouteNetwork.OWNER)) {
            throw new IllegalArgumentException("route maintenance work intent has invalid route ownership");
        }
        RouteMaintenance maintenance = intent.subjectIds().stream().map(state.routeMaintenances()::get).filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("route maintenance work intent lacks an active operation"));
        SubjectId cargoId = maintenance.cargoId().orElseThrow(() -> new IllegalArgumentException("route maintenance work intent has no cargo"));
        SubjectId itemId = intent.subjectIds().stream().filter(id -> !id.equals(FrontierRouteNetwork.OWNER) && !id.equals(maintenance.id()) && !id.equals(cargoId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("route maintenance work intent lacks an exact material"));
        ExactItemStack item = state.inventory().items().get(itemId); CargoBatch cargo = state.inventory().cargo().get(cargoId);
        PhysicalDelta loss = state.physicalDeltas().get(maintenance.repairCell());
        if (!maintenance.building() || cargo == null || !cargo.ownerId().equals(FrontierRouteNetwork.OWNER)
                || !cargo.itemIds().equals(java.util.List.of(itemId)) || !itemId.equals(maintenance.plannedCargoItemId())
                || item == null || !item.itemKind().equals(maintenance.expectedMaterial().repairItemKind())
                || !item.custody().equals(new InventoryCustody.Cargo(cargoId))
                || loss == null || loss.kind() != PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS
                || !loss.ownerId().equals(Optional.of(FrontierRouteNetwork.OWNER))
                || !loss.semanticPart().equals(Optional.of(maintenance.semanticPart()))
                || !wholeBlock(intent).equals(maintenance.repairCell())) {
            throw new IllegalArgumentException("route maintenance work intent lacks exact loss/cargo preconditions");
        }
    }

    public static void validateMaterialLoadingIntent(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent) {
        if (intent.kind() != io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING
                || intent.subjectIds().size() != 5 || !intent.subjectIds().getFirst().equals(FrontierRouteNetwork.OWNER)
                || !intent.causeSubjectId().equals(intent.subjectIds().get(1))) {
            throw new IllegalArgumentException("route maintenance pickup has invalid ownership");
        }
        RouteMaintenance maintenance = state.routeMaintenances().get(intent.causeSubjectId());
        if (maintenance == null || !maintenance.building() || maintenance.cargoId().isPresent()) {
            throw new IllegalArgumentException("route maintenance pickup has no unsupplied active operation");
        }
        SubjectId cargo = intent.subjectIds().get(2), cargoItem = intent.subjectIds().get(3), source = intent.subjectIds().get(4);
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
    }

    public static void validateMaterialLoadingReceipt(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                                      RouteMaintenanceMaterialLoadObservation observation) {
        validateMaterialLoadingIntent(state, intent);
        ExactItemStack source = state.inventory().items().get(observation.sourceItemId());
        if (!intent.id().equals(observation.intentId()) || !intent.causeSubjectId().equals(observation.maintenanceId())
                || !intent.subjectIds().get(2).equals(observation.cargoId()) || !intent.subjectIds().get(3).equals(observation.cargoItemId())
                || !intent.subjectIds().get(4).equals(observation.sourceItemId()) || source == null
                || observation.sourceRemainingCount() != source.count() - 1) {
            throw new IllegalArgumentException("route maintenance pickup receipt differs from prepared intent");
        }
    }

    static FrontierWorldState complete(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                       RouteMaintenanceObservation observation,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents) {
        validateWorkIntent(state, intent);
        RouteMaintenance maintenance = state.routeMaintenances().get(observation.maintenanceId());
        if (maintenance == null || !intent.subjectIds().contains(observation.itemId()) || !observation.position().equals(maintenance.repairCell())) {
            throw new IllegalArgumentException("route maintenance receipt differs from active repair");
        }
        Map<SubjectId, RouteMaintenance> maintenances = new LinkedHashMap<>(state.routeMaintenances()); maintenances.put(maintenance.id(), maintenance.ready().withoutCargo());
        Map<BlockPosition, PhysicalDelta> deltas = new LinkedHashMap<>(state.physicalDeltas()); deltas.remove(maintenance.repairCell());
        Map<io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId, SceneLease> leases = new LinkedHashMap<>(state.sceneLeases());
        leases.replaceAll((id, lease) -> FrontierSceneBehaviors.isEngineeringWorksite(lease)
                && FrontierSceneBehaviors.engineeringWorksite(lease).projectId().equals(maintenance.id())
                && lease.status() == SceneLeaseStatus.HOT ? lease.withStatus(SceneLeaseStatus.DRAINING) : lease);
        intents.put(intent.id(), intent.withStatus(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, Optional.of(observation.id())));
        Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new LinkedHashMap<>(state.physicalObservations()); observations.put(observation.id(), observation);
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().consumeCargoUnit(maintenance.cargoId().orElseThrow(), observation.itemId()))
                .physicalIntents(intents).physicalObservations(observations).physicalDeltas(deltas).routeMaintenances(maintenances).sceneLeases(leases)
                .routeTopology(state.routeTopology().reconcileSupplyAvailability(state.bootstrap(), deltas)));
    }

    static FrontierWorldState conflict(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent intent,
                                       Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent> intents) {
        RouteMaintenance maintenance = intent.subjectIds().stream().map(state.routeMaintenances()::get).filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("route maintenance conflict has no active operation"));
        Map<SubjectId, RouteMaintenance> maintenances = new LinkedHashMap<>(state.routeMaintenances()); maintenances.put(maintenance.id(), maintenance.conflict());
        intents.put(intent.id(), intent.withStatus(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty()));
        return state.withChanges(FrontierWorldStateUpdate.begin().physicalIntents(intents).routeMaintenances(maintenances));
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
