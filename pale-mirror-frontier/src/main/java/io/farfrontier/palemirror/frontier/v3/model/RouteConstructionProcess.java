package io.farfrontier.palemirror.frontier.v3.model;

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
final class RouteConstructionProcess {
    private static final SubjectId SYSTEM = new SubjectId("system:route-construction");
    private static final int[] DETOUR_SPINES = {-300, -260, -220, -180, -80, -40, 40, 80, 180, 220, 260, 300};
    private RouteConstructionProcess() { }

    static ScheduledAction scan(int ordinal, long dueAt) {
        return new ScheduledAction(new ScheduleId("schedule:route-construction-" + ordinal), new SimInstant(dueAt), 0,
                SYSTEM, "frontier.route_construction.scan", 1);
    }
    static ScheduledAction start(StrategicTask task, long dueAt) {
        if (task.kind() != StrategicTaskKind.CONSTRUCT_ROUTE_BYPASS) throw new IllegalArgumentException("invalid route construction task schedule");
        return new ScheduledAction(new ScheduleId("schedule:route-construction-start-" + task.id().value().replace(':', '-')), new SimInstant(dueAt), 0,
                task.id(), "frontier.route_construction.start", 1);
    }

    static List<ProposedEvent> planStart(FrontierWorldState state, ScheduledAction action) {
        StrategicTask task = constructionTaskById(state, action.subject(), StrategicTaskStatus.PENDING);
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), task.ownerId());
        boolean confirmed = task.dependencies().stream().map(state.strategicPlans().routePatrols()::get).anyMatch(patrol -> patrol != null
                && patrol.settlementId().equals(settlement.id()) && patrol.status() == RoutePatrolStatus.OBSTRUCTION_CONFIRMED
                && patrol.obstruction().stream().anyMatch(state.physicalDeltas()::containsKey));
        Optional<RouteConstruction> candidate = confirmed ? candidate(state, settlement) : Optional.empty();
        if (candidate.isEmpty()) return List.of(transition(task, StrategicTaskStatus.BLOCKED));
        return List.of(transition(task, StrategicTaskStatus.ACTIVE), new ProposedEvent(FrontierRouteNetwork.OWNER, new RouteConstructionStarted(candidate.orElseThrow())));
    }

    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value()) + 1;
        ProposedEvent next = new ProposedEvent(SYSTEM, new ScheduleEffect.Created(scan(ordinal, action.dueAt().ticks() + 100L)));
        if (state.physicalIntents().values().stream().anyMatch(intent -> intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION
                && (intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING))) return List.of(next);
        Optional<RouteConstruction> ready = state.routeConstructions().values().stream().filter(value -> value.status() == RouteConstructionStatus.READY)
                .sorted(Comparator.comparing(RouteConstruction::id)).findFirst();
        if (ready.isPresent()) {
            RouteConstruction value = ready.orElseThrow(); StrategicTask task = constructionTask(state, value.settlementId(), StrategicTaskStatus.ACTIVE);
            return List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, new RouteTopologyCutover(value.id())), transition(task, StrategicTaskStatus.COMPLETED), next);
        }
        Optional<RouteConstruction> project = state.routeConstructions().values().stream().filter(value -> value.status() == RouteConstructionStatus.BUILDING)
                .sorted(Comparator.comparing(RouteConstruction::id)).findFirst();
        if (project.isEmpty()) return List.of(next);
        List<BlockPosition> cells = FrontierRouteNetwork.constructionCells(state.bootstrap(), state.routeTopology(), project.orElseThrow().settlementId(), project.orElseThrow().waypoints());
        BlockPosition position = cells.get(project.orElseThrow().confirmedCells());
        Optional<ExactItemStack> material = state.inventory().items().values().stream()
                .filter(item -> item.itemKind().equals(GrayboxMaterial.ROUTE.repairItemKind()))
                .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(FrontierRouteNetwork.MAINTENANCE_CONTAINER)
                        && state.inventory().surfaces().get(slot.containerId()).status() == ContainerSurfaceStatus.ACTIVE)
                .sorted(Comparator.comparing(ExactItemStack::id)).findFirst();
        if (material.isEmpty()) return List.of(next);
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:route-build-" + project.orElseThrow().id().value().replace(':', '-') + "-" + project.orElseThrow().confirmedCells()),
                PhysicalIntentKind.ROUTE_CONSTRUCTION, PhysicalIntentStatus.PREPARED, FrontierRouteNetwork.OWNER,
                List.of(FrontierRouteNetwork.OWNER, project.orElseThrow().id(), material.orElseThrow().id()),
                new FixedPosition(FixedScalar.whole(position.x()), FixedScalar.whole(position.y()), FixedScalar.whole(position.z())), 0,
                PhysicalPostcondition.ROUTE_CONSTRUCTION_OBSERVED);
        return List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, new PhysicalIntentPrepared(intent)), next);
    }

    /**
     * Proposes one bounded canonical bypass for the first blocked settlement route.  The fixed
     * spine catalogue is intentional: observations can obstruct a route, but never turn an
     * arbitrary player road or a Minecraft pathfinding result into canonical topology.
     */
    private static Optional<RouteConstruction> candidate(FrontierWorldState state, Settlement settlement) {
        List<BlockPosition> current = state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id());
        BlockPosition origin = current.getFirst(), destination = current.getLast();
        BlockPosition detourEgress = origin.offset(-36, 0, 0), detourLane = detourEgress.offset(0, 0, 36);
        for (int spineX : DETOUR_SPINES) {
            List<BlockPosition> route = List.of(origin, detourEgress, detourLane, new BlockPosition(spineX, detourLane.y(), detourLane.z()),
                    new BlockPosition(spineX, destination.y(), destination.z()), destination);
            if (accepts(state, settlement.id(), route)) {
                return Optional.of(new RouteConstruction(projectId(settlement.id(), state), settlement.id(), route, 0, RouteConstructionStatus.BUILDING));
            }
        }
        return Optional.empty();
    }

    private static boolean accepts(FrontierWorldState state, SubjectId settlementId, List<BlockPosition> route) {
        try {
            RouteTopology topology = state.routeTopology().replaceSupplyRoute(state.bootstrap(), settlementId, route);
            return FrontierRouteNetwork.isPassable(state.bootstrap(), route, state.physicalDeltas())
                    && !FrontierRouteNetwork.constructionCells(state.bootstrap(), state.routeTopology(), settlementId, route).isEmpty()
                    && FrontierGrayboxPlan.compile(state.withRouteTopology(topology)).cells().size() > 0;
        } catch (IllegalArgumentException ignored) {
            return false;
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

    static FrontierWorldState reducePrepared(FrontierWorldState state, SubjectId subject, PhysicalIntent intent) {
        if (!subject.equals(FrontierRouteNetwork.OWNER) || intent.kind() != PhysicalIntentKind.ROUTE_CONSTRUCTION
                || !intent.causeSubjectId().equals(FrontierRouteNetwork.OWNER) || intent.subjectIds().size() != 3
                || !intent.subjectIds().contains(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route construction intent has an invalid owner");
        if (state.physicalIntents().values().stream().anyMatch(existing -> existing.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION
                && (existing.status() == PhysicalIntentStatus.PREPARED || existing.status() == PhysicalIntentStatus.RUNNING))) throw new IllegalArgumentException("only one route construction cell may be active");
        RouteConstructionStateSupport.validateIntent(state, intent);
        return state.preparePhysicalIntent(intent);
    }
    static List<ProposedEvent> planTransition(FrontierWorldState state, PhysicalIntent intent, PhysicalIntentTransition transition) {
        RouteConstruction project = intent.subjectIds().stream().map(state.routeConstructions()::get).filter(java.util.Objects::nonNull).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("route construction transition has no project"));
        StrategicTask task = constructionTask(state, project.settlementId(), StrategicTaskStatus.ACTIVE);
        ProposedEvent physical = new ProposedEvent(FrontierRouteNetwork.OWNER, transition);
        return transition.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART ? List.of(physical, transition(task, StrategicTaskStatus.BLOCKED)) : List.of(physical);
    }
    static StrategicTask constructionTask(FrontierWorldState state, SubjectId settlementId, StrategicTaskStatus status) {
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
