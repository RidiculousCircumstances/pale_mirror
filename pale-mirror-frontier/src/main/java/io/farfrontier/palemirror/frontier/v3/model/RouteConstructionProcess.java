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

    static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
        int ordinal = FrontierWorldScheduleSupport.ordinal(action.id().value()) + 1;
        ProposedEvent next = new ProposedEvent(SYSTEM, new ScheduleEffect.Created(scan(ordinal, action.dueAt().ticks() + 100L)));
        if (state.physicalIntents().values().stream().anyMatch(intent -> intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION
                && (intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING))) return List.of(next);
        Optional<RouteConstruction> ready = state.routeConstructions().values().stream().filter(value -> value.status() == RouteConstructionStatus.READY)
                .sorted(Comparator.comparing(RouteConstruction::id)).findFirst();
        if (ready.isPresent()) return List.of(new ProposedEvent(FrontierRouteNetwork.OWNER, new RouteTopologyCutover(ready.orElseThrow().id())), next);
        Optional<RouteConstruction> project = state.routeConstructions().values().stream().filter(value -> value.status() == RouteConstructionStatus.BUILDING)
                .sorted(Comparator.comparing(RouteConstruction::id)).findFirst();
        if (project.isEmpty()) {
            Optional<RouteConstruction> candidate = candidate(state);
            return candidate.<List<ProposedEvent>>map(value -> List.of(new ProposedEvent(FrontierRouteNetwork.OWNER,
                    new RouteConstructionStarted(value)), next)).orElseGet(() -> List.of(next));
        }
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
    private static Optional<RouteConstruction> candidate(FrontierWorldState state) {
        return state.bootstrap().settlements().stream().sorted(Comparator.comparing(Settlement::id)).filter(settlement ->
                !FrontierRouteNetwork.isPassable(state.bootstrap(), state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id()), state.physicalDeltas()))
                .filter(settlement -> state.routeConstructions().values().stream().noneMatch(project -> project.settlementId().equals(settlement.id())))
                .flatMap(settlement -> candidate(state, settlement).stream()).findFirst();
    }

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
}
