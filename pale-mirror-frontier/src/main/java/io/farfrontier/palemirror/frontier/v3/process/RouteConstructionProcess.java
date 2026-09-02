package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Plans at most one exact-material replacement-route cell at a time. */
public final class RouteConstructionProcess {
    private static final SubjectId SYSTEM = new SubjectId("system:route-construction");
    /** One replacement cell has a two-person reachable work front; larger projects compose bounded fronts. */
    private static final int INITIAL_ROUTE_REPAIR_CREW_SIZE = EngineeringRecoveryTeam.MIN_MEMBERS;
    private static final int[] DETOUR_SPINES = {-300, -260, -220, -180, -80, -40, 40, 80, 180, 220, 260, 300};
    private static final int[] DETOUR_LANE_OFFSETS = {60, -60, 80, -80, 36};
    private RouteConstructionProcess() { }

    public static ScheduledAction scan(int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:route-construction-" + ordinal), new SimInstant(dueAt), 0,
                SYSTEM, "frontier.route_construction.scan", 1);
    }
    public static ScheduledAction start(StrategicTask task, long dueAt) {
        if (task.kind() != StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS) throw new IllegalArgumentException("invalid route construction task schedule");
        return new ScheduledAction(new ScheduleId("schedule:route-construction-start-" + task.id().value().replace(':', '-')), new SimInstant(dueAt), 0,
                task.id(), "frontier.route_construction.start", 1);
    }

    /** One exact COLD step for an already-admitted crew; it is not a strategic review. */
    public static ScheduledAction assemblyProgress(SubjectId projectId, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:route-construction-assembly-" + projectId.value().replace(':', '-')),
                new SimInstant(dueAt), 0, projectId, "frontier.route_construction.assembly_progress", 1);
    }

    public static List<ProposedEvent> planStart(FrontierWorldState state, ScheduledAction action) {
        StrategicTask task = constructionTaskById(state, action.subject(), StrategicTaskStatus.PENDING);
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), task.ownerId());
        if (RouteMaintenanceProcess.blocksBypassConstruction(state, settlement.id())) return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        boolean confirmed = task.dependencies().stream().map(state.strategicPlans().routePatrols()::get).anyMatch(patrol -> patrol != null
                && patrol.settlementId().equals(settlement.id()) && patrol.status() == RoutePatrolStatus.OBSTRUCTION_CONFIRMED
                && patrol.obstruction().stream().anyMatch(state.physicalDeltas()::containsKey));
        Optional<RouteConstruction> candidate = confirmed ? candidate(state, settlement) : Optional.empty();
        if (candidate.isEmpty()) return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        RouteConstruction project = candidate.orElseThrow();
        try {
            // This is construction-task admission, not catalogue exploration: compile the
            // exact crew approach once for the accepted topology. Repeating it for every
            // speculative spine turned a bounded compiler into an accidental O(candidates)
            // scan of route geometry.
            EngineeringWorksite.compile(state, project);
        } catch (IllegalArgumentException unavailable) {
            return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        }
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(FrontierRouteNetwork.OWNER, new RouteConstructionStarted(project)));
    }

    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value()) + 1;
        ProposedEvent next = new ProposedEvent(SYSTEM, new ScheduleEffect.Created(scan(ordinal, action.dueAt().ticks()
                + state.bootstrap().ruleset().cadence().routeConstructionScanInterval())));
        Optional<RouteConstruction> activeProject = state.routeConstructions().values().stream().filter(value -> value.status() == RouteConstructionStatus.BUILDING)
                .sorted(Comparator.comparing(RouteConstruction::id)).findFirst();
        // Physical work serializes only with its own durable intent.  A field harvest, another
        // settlement's surface transition or a finished unrelated operation has no authority
        // to freeze an already admitted construction crew indefinitely.
        RouteConstruction active = activeProject.orElse(null);
        if (active != null && state.physicalIntents().values().stream().anyMatch(intent -> (intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION
                || intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING) && intent.subjectIds().contains(active.id())
                && (intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING))) return List.of(next);
        Optional<RouteConstruction> ready = state.routeConstructions().values().stream().filter(value -> value.status() == RouteConstructionStatus.READY)
                .sorted(Comparator.comparing(RouteConstruction::id)).findFirst();
        if (ready.isPresent()) {
            RouteConstruction value = ready.orElseThrow(); StrategicTask task = constructionTask(state, value.settlementId(), StrategicTaskStatus.ACTIVE);
            if (value.team().isPresent() && !EngineeringEquipmentProcess.returnedOrLost(state, value)) {
                return EngineeringEquipmentProcess.returnOne(state, value).map(intent -> List.of(new ProposedEvent(value.settlementId(),
                        new PhysicalIntentPrepared(intent)), next)).orElse(List.of(next));
            }
            return List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, new RouteTopologyCutover(value.id())), transition(task, StrategicTaskStatus.COMPLETED), next);
        }
        Optional<RouteConstruction> project = activeProject;
        if (project.isEmpty()) return List.of(next);
        RouteConstruction current = project.orElseThrow();
        if (current.team().isPresent() && !EngineeringToolCustody.ready(state, current.team().orElseThrow())) {
            return EngineeringEquipmentProcess.issueOne(state, current).map(intent -> List.of(new ProposedEvent(current.settlementId(),
                    new PhysicalIntentPrepared(intent)), next)).orElse(List.of(next));
        }
        if (current.team().isPresent() && current.assembly().isEmpty()) {
            return planEngineeringAssemblyAdmission(state, current, action, next);
        }
        if (current.team().isPresent() && !current.assembly().orElseThrow().complete()) {
            return List.of(next);
        }
        if (current.cargoId().isEmpty()) {
            Optional<ExactItemStack> material = maintenanceMaterial(state);
            ContainerSurface surface = state.inventory().surfaces().get(FrontierRouteNetwork.MAINTENANCE_CONTAINER);
            if (material.isEmpty() || surface == null || surface.status() != ContainerSurfaceStatus.ACTIVE) return List.of(next);
            return List.of(new ProposedEvent(FrontierRouteNetwork.OWNER,
                    new PhysicalIntentPrepared(materialLoadingIntent(current, material.orElseThrow(), surface))), next);
        }
        SubjectId cargoId = current.cargoId().orElseThrow(); CargoBatch cargo = state.inventory().cargo().get(cargoId);
        if (cargo == null || cargo.itemIds().size() != 1) return List.of(next);
        ExactItemStack material = state.inventory().items().get(cargo.itemIds().getFirst());
        if (material == null || !material.custody().equals(new InventoryCustody.Cargo(cargoId))
                || !material.itemKind().equals(GrayboxMaterial.ROUTE.repairItemKind())) return List.of(next);
        // A retained engineering team has reached its immutable work site.  Its naturally
        // loaded HOT lease, not this COLD scheduler, is now the only owner allowed to open the
        // physical placement intent. Legacy no-team projects retain their old migration path.
        if (current.team().isPresent()) return List.of(next);
        return List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, new PhysicalIntentPrepared(workIntent(current, cargoId, material.id()))), next);
    }

    /**
     * COLD preserves the exact approach before a future HOT work-site lease may materialize it.
     * It moves one retained living crew member by one precompiled cell, never spawns a shortcut
     * at the construction cell and never advances a body already under ambient Minecraft authority.
     */
    private static List<ProposedEvent> planEngineeringAssemblyAdmission(FrontierWorldState state, RouteConstruction project,
                                                                          ScheduledAction action, ProposedEvent next) {
        boolean retainedHotOrRecovery = state.sceneLeases().values().stream()
                .filter(FrontierSceneBehaviors::isEngineeringWorksite)
                .anyMatch(lease -> FrontierSceneBehaviors.engineeringWorksite(lease).projectId().equals(project.id())
                        && lease.status() != SceneLeaseStatus.CLOSED);
        if (retainedHotOrRecovery) return List.of(next);
        // A COLD cursor is born only after every predecessor ambient body has been durably
        // drained.  Compiling from an old COLD location while a loaded body is still governed
        // by WORK/PATROL would create two positions for one person.  The next scan retries
        // after ordinary HOT drain, which records the body's actual final floor first.
        if (project.team().orElseThrow().memberIds().stream().map(state.ambientLeases()::get)
                .anyMatch(lease -> lease != null && lease.status() != AmbientLeaseStatus.CLOSED)) return List.of(next);
        return List.of(new ProposedEvent(FrontierRouteNetwork.OWNER,
                new RouteConstructionAssemblyStarted(project.id(), EngineeringWorksite.compile(state, project))),
                new ProposedEvent(SYSTEM, new ScheduleEffect.Created(assemblyProgress(project.id(), nextAssemblyDue(state, action.dueAt().ticks())))), next);
    }

    /**
     * Advances an immutable crew corridor on the same human COLD-movement cadence as migration.
     * The slower construction scan still owns strategic admission, tools, cargo and cutover; it
     * must never become the clock for a person walking one retained cell.
     */
    public static List<ProposedEvent> planAssemblyProgress(FrontierWorldState state, ScheduledAction action) {
        RouteConstruction project = state.routeConstructions().get(action.subject());
        if (project == null || !assemblyProgress(project.id(), action.dueAt().ticks()).id().equals(action.id())
                || project.status() != RouteConstructionStatus.BUILDING || project.team().isEmpty() || project.assembly().isEmpty()) return List.of();
        EngineeringWorkAssembly assembly = project.assembly().orElseThrow();
        if (assembly.complete()) return List.of();
        ProposedEvent next = new ProposedEvent(SYSTEM, new ScheduleEffect.Created(assemblyProgress(project.id(), nextAssemblyDue(state, action.dueAt().ticks()))));
        boolean retainedHotOrRecovery = state.sceneLeases().values().stream()
                .filter(FrontierSceneBehaviors::isEngineeringWorksite)
                .anyMatch(lease -> FrontierSceneBehaviors.engineeringWorksite(lease).projectId().equals(project.id())
                        && lease.status() != SceneLeaseStatus.CLOSED);
        if (retainedHotOrRecovery) return List.of(next);
        SubjectId advancing = assembly.safeAdvances().stream().filter(member -> {
            AmbientActorLease lease = state.ambientLeases().get(member);
            return lease == null || lease.status() == AmbientLeaseStatus.CLOSED;
        }).findFirst().orElse(null);
        if (advancing == null) return List.of(next);
        return List.of(new ProposedEvent(FrontierRouteNetwork.OWNER,
                new RouteConstructionAssemblyAdvanced(project.id(), assembly.advance(advancing))), next);
    }

    private static long nextAssemblyDue(FrontierWorldState state, long dueAt) {
        return Math.addExact(dueAt, state.bootstrap().ruleset().cadence().migrationStepInterval());
    }

    /**
     * Proposes one bounded canonical bypass for the first blocked settlement route.  The fixed
     * spine catalogue is intentional: observations can obstruct a route, but never turn an
     * arbitrary player road or a Minecraft pathfinding result into canonical topology.
     */
    private static Optional<RouteConstruction> candidate(FrontierWorldState state, Settlement settlement) {
        List<BlockPosition> current = state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id());
        BlockPosition origin = current.getFirst(), destination = current.getLast();
        BlockPosition detourEgress = origin.offset(-36, 0, 0);
        for (int laneOffset : DETOUR_LANE_OFFSETS) {
            BlockPosition detourLane = detourEgress.offset(0, 0, laneOffset);
            for (int spineX : DETOUR_SPINES) {
                List<BlockPosition> route = List.of(origin, detourEgress, detourLane, new BlockPosition(spineX, detourLane.y(), detourLane.z()),
                        new BlockPosition(spineX, destination.y(), destination.z()), destination);
                Optional<List<BlockPosition>> workCells = acceptedWorkCells(state, settlement.id(), route);
                if (workCells.isPresent()) {
                    SubjectId projectId = projectId(settlement.id(), state);
                    EngineeringRecoveryTeam team = team(state, settlement.id(), projectId);
                    if (team != null) {
                        return Optional.of(new RouteConstruction(projectId, settlement.id(), route, workCells.orElseThrow(), 0,
                                RouteConstructionStatus.BUILDING, Optional.empty(), Optional.of(team), Optional.empty()));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Admits only free, living exact people from the owner settlement.  The team is an explicit
     * opportunity cost: no construction can silently borrow a farmer, cargo crew or defender.
     * Tool issue remains its own later owner; this admission deliberately does not pretend that
     * engineering capability is an item grant.
     */
    private static EngineeringRecoveryTeam team(FrontierWorldState state, SubjectId settlementId, SubjectId projectId) {
        HumanAssignmentProjection assignments = HumanAssignmentProjection.compile(state);
        List<SubjectId> members = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlementId))
                .filter(resident -> assignments.idle(resident.id()))
                .filter(resident -> FrontierWorldStateSupport.workCapable(state, resident))
                .filter(resident -> resident.capability(HumanCapability.ENGINEERING) > 0)
                .sorted(Comparator.comparing((ResidentProfile resident) -> resident.profession() != ResidentProfession.ENGINEER)
                        .thenComparing(Comparator.comparing((ResidentProfile resident) -> resident.capability(HumanCapability.ENGINEERING)).reversed())
                        .thenComparing(ResidentProfile::id))
                .limit(INITIAL_ROUTE_REPAIR_CREW_SIZE).map(ResidentProfile::id).toList();
        return members.isEmpty() ? null : EngineeringRecoveryTeam.forWorkOrder(projectId, settlementId, members);
    }

    private static Optional<ExactItemStack> maintenanceMaterial(FrontierWorldState state) {
        return state.inventory().items().values().stream().filter(item -> item.itemKind().equals(GrayboxMaterial.ROUTE.repairItemKind()))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(FrontierRouteNetwork.MAINTENANCE_CONTAINER))
                .sorted(Comparator.comparing(ExactItemStack::id)).findFirst();
    }

    private static PhysicalIntent materialLoadingIntent(RouteConstruction project, ExactItemStack material, ContainerSurface surface) {
        SubjectId cargo = cargoId(project); BlockPosition position = surface.position();
        // A project consumes a new exact unit for every cell.  Completed intents remain
        // durable audit evidence, so the pickup identity must be scoped to that cell rather
        // than reused when the next unit is requested after a restart.
        return new PhysicalIntent(new PhysicalIntentId("intent:route-material-load-" + project.id().value().replace(':', '-') + "-" + project.confirmedCells()),
                PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING, PhysicalIntentStatus.PREPARED, project.id(),
                List.of(FrontierRouteNetwork.OWNER, project.id(), cargo, cargoItemId(project), material.id()),
                new FixedPosition(FixedScalar.whole(position.x()), FixedScalar.whole(position.y()), FixedScalar.whole(position.z())), 0,
                PhysicalPostcondition.ROUTE_CONSTRUCTION_MATERIAL_LOADED_OBSERVED);
    }
    public static SubjectId cargoId(RouteConstruction project) {
        return new SubjectId("cargo:route-build-" + project.id().value().substring("construction:".length()) + "-" + project.confirmedCells());
    }
    public static SubjectId cargoItemId(RouteConstruction project) {
        return new SubjectId("item:route-build-" + project.id().value().substring("construction:".length()) + "-" + project.confirmedCells());
    }

    /** Exact next-cell physical work intent; admission is separately guarded by its HOT scene. */
    public static PhysicalIntent workIntent(RouteConstruction project, SubjectId cargoId, SubjectId materialId) {
        BlockPosition position = project.workCells().get(project.confirmedCells());
        return new PhysicalIntent(new PhysicalIntentId("intent:route-build-" + project.id().value().replace(':', '-') + "-" + project.confirmedCells()),
                PhysicalIntentKind.ROUTE_CONSTRUCTION, PhysicalIntentStatus.PREPARED, FrontierRouteNetwork.OWNER,
                List.of(FrontierRouteNetwork.OWNER, project.id(), cargoId, materialId),
                new FixedPosition(FixedScalar.whole(position.x()), FixedScalar.whole(position.y()), FixedScalar.whole(position.z())), 0,
                PhysicalPostcondition.ROUTE_CONSTRUCTION_OBSERVED);
    }

    private static Optional<List<BlockPosition>> acceptedWorkCells(FrontierWorldState state, SubjectId settlementId, List<BlockPosition> route) {
        try {
            RouteTopology topology = state.routeTopology().replaceSupplyRoute(state.bootstrap(), settlementId, route);
            List<BlockPosition> workCells = FrontierRouteNetwork.constructionCells(state.bootstrap(), state.routeTopology(), settlementId, route);
            if (!FrontierRouteNetwork.isPassable(state.bootstrap(), route, state.physicalDeltas()) || workCells.isEmpty()
                    || FrontierGrayboxPlan.compile(state.withRouteTopology(topology)).cells().isEmpty()) return Optional.empty();
            return Optional.of(workCells);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static SubjectId projectId(SubjectId settlementId, FrontierWorldState state) {
        String prefix = "construction:route-reroute-" + settlementId.value().replace(':', '-');
        Optional<BlockPosition> cause = state.physicalDeltas().keySet().stream().sorted(Comparator.comparingInt(BlockPosition::x)
                .thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z)).filter(position -> routeContains(state, settlementId, position)).findFirst();
        BlockPosition position = cause.orElseThrow();
        return new SubjectId(prefix + "-" + position.x() + "-" + position.y() + "-" + position.z());
    }

    private static boolean routeContains(FrontierWorldState state, SubjectId settlementId, BlockPosition position) {
        return FrontierRouteNetwork.containsOperationSurfaceCell(state.routeTopology().supplyWaypoints(state.bootstrap(), settlementId), position);
    }

    public static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        if (!subject.equals(FrontierRouteNetwork.OWNER) || intent.kind() != PhysicalIntentKind.ROUTE_CONSTRUCTION
                || !intent.causeSubjectId().equals(FrontierRouteNetwork.OWNER) || intent.subjectIds().size() != 4
                || !intent.subjectIds().contains(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route construction intent has an invalid owner");
        if (state.physicalIntents().values().stream().anyMatch(existing -> existing.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION
                && (existing.status() == PhysicalIntentStatus.PREPARED || existing.status() == PhysicalIntentStatus.RUNNING))) throw new IllegalArgumentException("only one route construction cell may be active");
        RouteConstructionStateSupport.validateIntent(state, intent);
        return state.preparePhysicalIntent(intent);
    }
    public static List<ProposedEvent> planTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition) {
        RouteConstruction project = intent.subjectIds().stream().map(state.routeConstructions()::get).filter(java.util.Objects::nonNull).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("route construction transition has no project"));
        StrategicTask task = constructionTask(state, project.settlementId(), StrategicTaskStatus.ACTIVE);
        ProposedEvent physical = new ProposedEvent(FrontierRouteNetwork.OWNER, transition);
        return transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART ? List.of(physical, transition(task, StrategicTaskStatus.BLOCKED)) : List.of(physical);
    }
    public static List<ProposedEvent> planMaterialLoadingTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition) {
        RouteConstructionStateSupport.validateMaterialLoadingIntent(state, intent);
        RouteConstruction project = state.routeConstructions().get(intent.causeSubjectId());
        if (project == null) throw new IllegalArgumentException("route construction material loading has no project");
        StrategicTask task = constructionTask(state, project.settlementId(), StrategicTaskStatus.ACTIVE);
        ProposedEvent physical = new ProposedEvent(FrontierRouteNetwork.OWNER, transition);
        if (transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART) return List.of(physical, transition(task, StrategicTaskStatus.BLOCKED));
        if (transition.status() != PhysicalIntentStatus.CONFIRMED) return List.of(physical);
        if (!(transition.observation().orElseThrow() instanceof RouteConstructionMaterialLoadObservation observation)) {
            throw new IllegalArgumentException("route construction material loading requires its exact observation");
        }
        RouteConstructionStateSupport.validateMaterialLoadingReceipt(state, intent, observation);
        return List.of(physical, new ProposedEvent(FrontierRouteNetwork.OWNER,
                new RouteConstructionMaterialLoaded(project.id(), new CargoBatch(observation.cargoId(), FrontierRouteNetwork.OWNER, List.of(observation.cargoItemId())))));
    }
    public static StrategicTask constructionTask(FrontierWorldState state, SubjectId settlementId, StrategicTaskStatus status) {
        return state.strategicPlans().tasks().values().stream().filter(task -> task.ownerId().equals(settlementId)
                && task.kind() == StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS && task.status() == status).reduce((left, right) -> {
                    throw new IllegalArgumentException("route construction task binding is ambiguous");
                }).orElseThrow(() -> new IllegalArgumentException("route construction has no matching strategic task"));
    }
    private static StrategicTask constructionTaskById(FrontierWorldState state, SubjectId taskId, StrategicTaskStatus status) {
        StrategicTask task = state.strategicPlans().tasks().get(taskId);
        if (task == null || task.kind() != StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS || task.status() != status) {
            throw new IllegalArgumentException("route construction has no matching task identity");
        }
        return task;
    }
    private static ProposedEvent transition(StrategicTask task, StrategicTaskStatus status) {
        return new ProposedEvent(task.ownerId(), new StrategicTaskTransition(task.id(), status));
    }
}
