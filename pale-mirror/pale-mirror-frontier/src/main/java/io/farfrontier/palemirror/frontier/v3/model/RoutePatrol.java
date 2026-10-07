package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One exact patrol's retained ingress, inspection topology and column state.
 *
 * <p>The old waypoint/cursor representation is deliberately absent. A patrol
 * first moves each named resident through {@link PatrolAssembly}; only after
 * that ingress completes can it advance its distinct-body {@link PatrolTravel}
 * formation. The complete inspected route remains separate because its first
 * edge is crossed by ingress and can still carry an observed obstruction.</p>
 */
public record RoutePatrol(SubjectId taskId, SubjectId settlementId, RouteUnitManifest unit,
                          TraversalTopology inspectionRoute, PatrolAssembly assembly,
                          PatrolTravel travel, TacticalPlan tacticalPlan, RoutePatrolStatus status,
                          Optional<BlockPosition> obstruction, Optional<RoutePatrolBlockReason> blockReason) {
    /** Legacy fixture constructor; production admission always supplies the task-bound plan. */
    public RoutePatrol(SubjectId taskId, SubjectId settlementId, RouteUnitManifest unit, TraversalTopology inspectionRoute,
                       PatrolAssembly assembly, PatrolTravel travel, RoutePatrolStatus status, Optional<BlockPosition> obstruction) {
        this(taskId, settlementId, unit, inspectionRoute, assembly, travel,
                TacticalPlan.routePatrol(new StrategicTask(taskId, new SubjectId("objective:implicit-" + taskId.value().replace(':', '-')),
                        settlementId, StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE, Optional.empty(),
                        List.of(StrategicTaskRequirement.AVAILABLE_GUARD), List.of(), StrategicTaskStatus.PENDING), unit,
                        inspectionRoute.linearCorridorSurfaces().stream().map(SurfaceAnchor::support).toList()), status, obstruction, Optional.empty());
    }
    /** Compatibility constructor for current-format callers that do not form a terminal block. */
    public RoutePatrol(SubjectId taskId, SubjectId settlementId, RouteUnitManifest unit, TraversalTopology inspectionRoute,
                       PatrolAssembly assembly, PatrolTravel travel, TacticalPlan tacticalPlan, RoutePatrolStatus status,
                       Optional<BlockPosition> obstruction) {
        this(taskId, settlementId, unit, inspectionRoute, assembly, travel, tacticalPlan, status, obstruction, Optional.empty());
    }
    /** Compiles the one permitted human ingress and inspection column at patrol admission. */
    public static RoutePatrol planned(FrontierWorldState state, SubjectId taskId, Settlement settlement, RouteUnitManifest unit) {
        return planned(state, new StrategicTask(taskId, new SubjectId("objective:implicit-" + taskId.value().replace(':', '-')),
                settlement.id(), StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE, Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_GUARD),
                List.of(), StrategicTaskStatus.PENDING), settlement, unit);
    }
    /** Compiles an inspection topology from the exact durable patrol task cause. */
    public static RoutePatrol planned(FrontierWorldState state, StrategicTask task, Settlement settlement, RouteUnitManifest unit) {
        Objects.requireNonNull(state, "patrol planning state"); Objects.requireNonNull(task, "patrol planning task");
        Objects.requireNonNull(settlement, "patrol planning settlement"); Objects.requireNonNull(unit, "patrol planning unit");
        if (task.kind() != StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE || !task.ownerId().equals(settlement.id())) {
            throw new IllegalArgumentException("patrol planning task has a foreign owner or kind");
        }
        SubjectId taskId = task.id();
        TraversalTopology inspection = inspectionTopology(state, task, settlement);
        PatrolAssembly assembly = PatrolAssemblyCorridor.compile(state, taskId, settlement, unit, inspection);
        TraversalTopology leaderRoute = inspection.linearSegment(new TraversalTopologyId("topology:patrol-travel:" + taskId.value() + ":leader"),
                1, inspection.linearCorridorSurfaces().size() - 1);
        SubjectId scout = unit.memberIds().stream().filter(member -> !member.equals(unit.leaderId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("patrol unit has no scout"));
        TraversalTopology scoutRoute = inspection.linearSegment(new TraversalTopologyId("topology:patrol-travel:" + taskId.value() + ":scout"),
                0, inspection.linearCorridorSurfaces().size() - 2);
        PatrolTravel travel = new PatrolTravel(unit.leaderId(), leaderRoute, java.util.Map.of(unit.leaderId(), new PatrolTravel.Member(leaderRoute, 0),
                scout, new PatrolTravel.Member(scoutRoute, 0)));
        return new RoutePatrol(taskId, settlement.id(), unit, inspection, assembly, travel,
                TacticalPlan.routePatrol(task, unit, inspection.linearCorridorSurfaces().stream().map(SurfaceAnchor::support).toList()),
                RoutePatrolStatus.ASSEMBLING, Optional.empty(), Optional.empty());
    }

    /** The operation route is the durable causal plan; it is not rediscovered from Minecraft. */
    public static TraversalTopology inspectionTopology(FrontierWorldState state, StrategicTask task, Settlement settlement) {
        Objects.requireNonNull(state, "patrol inspection state"); Objects.requireNonNull(task, "patrol inspection task");
        Objects.requireNonNull(settlement, "patrol inspection settlement");
        return state.routeTopology().settlementTraversalTopology(state.bootstrap(), settlement.id());
    }

    public RoutePatrol {
        taskId = Objects.requireNonNull(taskId, "patrol task"); settlementId = Objects.requireNonNull(settlementId, "patrol settlement");
        unit = Objects.requireNonNull(unit, "patrol unit"); inspectionRoute = requireInspectionRoute(inspectionRoute);
        assembly = Objects.requireNonNull(assembly, "patrol assembly"); travel = Objects.requireNonNull(travel, "patrol travel");
        tacticalPlan = Objects.requireNonNull(tacticalPlan, "patrol tactical plan");
        status = Objects.requireNonNull(status, "patrol status"); obstruction = Objects.requireNonNull(obstruction, "patrol obstruction");
        blockReason = Objects.requireNonNull(blockReason, "patrol block reason");
        if (unit.kind() != RouteUnitKind.PATROL || !unit.ownerId().equals(taskId)
                || !unit.id().equals(RouteUnitManifest.idFor(RouteUnitKind.PATROL, taskId))) {
            throw new IllegalArgumentException("patrol must own its exact patrol unit");
        }
        if (!assembly.members().keySet().equals(new java.util.LinkedHashSet<>(unit.memberIds()))
                || !travel.members().keySet().equals(assembly.members().keySet()) || !travel.leaderId().equals(unit.leaderId())) {
            throw new IllegalArgumentException("patrol movement state must retain exactly its named unit");
        }
        if (!tacticalPlan.operationId().equals(taskId) || !tacticalPlan.objectiveIds().equals(List.of(taskId))
                || !tacticalPlan.policy().equals(TacticalPolicyRegistry.ROUTE_PATROL)) {
            throw new IllegalArgumentException("patrol must retain its own route tactical plan");
        }
        tacticalPlan.validateMembers(unit.memberIds());
        List<SurfaceAnchor> inspection = inspectionRoute.linearCorridorSurfaces();
        if (!travel.leaderRoute().linearCorridorSurfaces().equals(inspection.subList(1, inspection.size()))) {
            throw new IllegalArgumentException("patrol leader route must retain the inspected route after its ingress edge");
        }
        SubjectId leaderId = unit.leaderId();
        PatrolTravel.Member scout = travel.members().entrySet().stream().filter(entry -> !entry.getKey().equals(leaderId))
                .map(java.util.Map.Entry::getValue).findFirst().orElseThrow();
        if (!scout.topology().linearCorridorSurfaces().equals(inspection.subList(0, inspection.size() - 1))) {
            throw new IllegalArgumentException("patrol scout route must retain the same inspected column one cell behind");
        }
        java.util.Map<SubjectId, BodyPosition> travelBodies = travel.bodies();
        if (status == RoutePatrolStatus.ASSEMBLING && !assembly.members().entrySet().stream()
                .allMatch(entry -> entry.getValue().destinationBody().equals(travelBodies.get(entry.getKey())))) {
            throw new IllegalArgumentException("patrol ingress destinations must be the retained travel formation");
        }
        if ((status == RoutePatrolStatus.OBSTRUCTION_CONFIRMED) != obstruction.isPresent()) {
            throw new IllegalArgumentException("patrol evidence does not match its status");
        }
        if ((status == RoutePatrolStatus.BLOCKED) != blockReason.isPresent()) {
            throw new IllegalArgumentException("patrol block status must retain one typed owner reason");
        }
        if (status == RoutePatrolStatus.ROUTE_CLEAR && !travel.complete()) throw new IllegalArgumentException("clear patrol must finish its whole column");
        if (status == RoutePatrolStatus.ASSEMBLING && assembly.complete()) throw new IllegalArgumentException("completed ingress must atomically enter route travel");
    }

    public List<SubjectId> memberIds() { return unit.memberIds(); }
    public SubjectId guardId() { return unit.leaderId(); }
    public List<BlockPosition> route() { return inspectionRoute.linearCorridorSurfaces().stream().map(SurfaceAnchor::support).toList(); }
    /** Cursor on the full inspected route, even though travel begins after ingress edge zero. */
    public int routeIndex() { return status == RoutePatrolStatus.ASSEMBLING ? 0 : travel.routeCursor() + 1; }
    public boolean active() { return status == RoutePatrolStatus.ASSEMBLING || status == RoutePatrolStatus.EN_ROUTE; }
    public List<SubjectId> safeAdvances() { return switch (status) {
        case ASSEMBLING -> assembly.safeAdvances().stream().sorted(java.util.Comparator.comparing((SubjectId id) -> !id.equals(unit.leaderId()))
                .thenComparing(java.util.Comparator.naturalOrder())).toList();
        case EN_ROUTE -> travel.safeAdvances();
        default -> List.of();
    }; }

    /**
     * Admission-only proof that the independently retained home ingress paths
     * can reach their distinct formation cells without an occupied-cell cycle.
     * It is evaluated over immutable topology, never a loaded Minecraft world.
     */
    public boolean assemblyCanReachFormation() {
        PatrolAssembly current = assembly;
        int maximumTransitions = current.members().values().stream().mapToInt(member -> member.corridor().size() - 1).sum();
        for (int transition = 0; transition <= maximumTransitions; transition++) {
            if (current.complete()) return true;
            List<SubjectId> safe = current.safeAdvances();
            if (safe.isEmpty()) return false;
            current = current.advanceOne(safe.getFirst());
        }
        throw new IllegalStateException("bounded patrol ingress exceeded its retained transition count");
    }
    public RoutePatrol advance(SubjectId actorId) {
        SubjectId actor = Objects.requireNonNull(actorId, "patrol advancing actor");
        if (status == RoutePatrolStatus.ASSEMBLING) {
            PatrolAssembly nextAssembly = assembly.advanceOne(actor);
            TacticalPlan nextPlan = nextAssembly.complete() ? tacticalPlan.withPhase(TacticalPlanPhase.TRAVEL) : tacticalPlan;
            return new RoutePatrol(taskId, settlementId, unit, inspectionRoute, nextAssembly, travel, nextPlan,
                    nextAssembly.complete() ? RoutePatrolStatus.EN_ROUTE : RoutePatrolStatus.ASSEMBLING, Optional.empty(), Optional.empty());
        }
        if (status == RoutePatrolStatus.EN_ROUTE) {
            PatrolTravel nextTravel = travel.advanceOne(actor);
            TacticalPlan nextPlan = nextTravel.complete() ? tacticalPlan.withPhase(TacticalPlanPhase.COMPLETE) : tacticalPlan;
            return new RoutePatrol(taskId, settlementId, unit, inspectionRoute, assembly, nextTravel, nextPlan,
                    nextTravel.complete() ? RoutePatrolStatus.ROUTE_CLEAR : RoutePatrolStatus.EN_ROUTE, Optional.empty(), Optional.empty());
        }
        throw new IllegalArgumentException("only an active patrol may advance");
    }
    public RoutePatrol withSpatialContinuation(PatrolAssembly nextAssembly, PatrolTravel nextTravel) {
        if (!active()) throw new IllegalArgumentException("terminal patrol has no advancing spatial continuation");
        return new RoutePatrol(taskId, settlementId, unit, inspectionRoute, nextAssembly, nextTravel,
                tacticalPlan, status, obstruction, blockReason);
    }
    /** Advances the exact retained column by one shared edge; callers cannot choose a guard. */
    public RoutePatrol advanceFormation() {
        if (status == RoutePatrolStatus.ASSEMBLING) {
            PatrolAssembly nextAssembly = assembly.advanceFormation();
            TacticalPlan nextPlan = nextAssembly.complete() ? tacticalPlan.withPhase(TacticalPlanPhase.TRAVEL) : tacticalPlan;
            return new RoutePatrol(taskId, settlementId, unit, inspectionRoute, nextAssembly, travel, nextPlan,
                    nextAssembly.complete() ? RoutePatrolStatus.EN_ROUTE : RoutePatrolStatus.ASSEMBLING, Optional.empty(), Optional.empty());
        }
        if (status != RoutePatrolStatus.EN_ROUTE) throw new IllegalArgumentException("only an active patrol may advance its formation");
        PatrolTravel nextTravel = travel.advanceFormation();
        TacticalPlan nextPlan = nextTravel.complete() ? tacticalPlan.withPhase(TacticalPlanPhase.COMPLETE) : tacticalPlan;
        return new RoutePatrol(taskId, settlementId, unit, inspectionRoute, assembly, nextTravel, nextPlan,
                nextTravel.complete() ? RoutePatrolStatus.ROUTE_CLEAR : RoutePatrolStatus.EN_ROUTE, Optional.empty(), Optional.empty());
    }
    public RoutePatrol confirm(BlockPosition position) {
        if (!active() || !FrontierRouteNetwork.containsOperationSurfaceCell(route(), Objects.requireNonNull(position, "patrol obstruction"))) {
            throw new IllegalArgumentException("patrol cannot confirm a foreign obstruction");
        }
        return new RoutePatrol(taskId, settlementId, unit, inspectionRoute, assembly, travel,
                tacticalPlan.withPhase(TacticalPlanPhase.CONTACT), RoutePatrolStatus.OBSTRUCTION_CONFIRMED, Optional.of(position), Optional.empty());
    }
    public RoutePatrol block(RoutePatrolBlockReason reason) {
        if (!active()) throw new IllegalArgumentException("only an active patrol may block");
        return new RoutePatrol(taskId, settlementId, unit, inspectionRoute, assembly, travel,
                tacticalPlan.withPhase(TacticalPlanPhase.ABORTED), RoutePatrolStatus.BLOCKED, Optional.empty(), Optional.of(Objects.requireNonNull(reason, "patrol block reason")));
    }
    public RoutePatrol fail() {
        if (!active()) throw new IllegalArgumentException("only an active patrol may fail");
        return new RoutePatrol(taskId, settlementId, unit, inspectionRoute, assembly, travel,
                tacticalPlan.withPhase(TacticalPlanPhase.ABORTED), RoutePatrolStatus.FAILED, Optional.empty(), Optional.empty());
    }

    private static TraversalTopology requireInspectionRoute(TraversalTopology route) {
        route = Objects.requireNonNull(route, "patrol inspection route");
        if (route.edges().stream().anyMatch(edge -> edge.kind() != TraversalKind.PEDESTRIAN
                || !edge.capabilities().contains(TraversalCapability.PEDESTRIAN)) || route.linearCorridorSurfaces().size() < 3) {
            throw new IllegalArgumentException("patrol inspection route must be a bounded pedestrian corridor");
        }
        return route;
    }
    private static long revision(List<BlockPosition> route) {
        long hash = 0xcbf29ce484222325L;
        for (BlockPosition position : route) {
            hash = (hash ^ Integer.toUnsignedLong(position.x())) * 0x100000001b3L;
            hash = (hash ^ Integer.toUnsignedLong(position.y())) * 0x100000001b3L;
            hash = (hash ^ Integer.toUnsignedLong(position.z())) * 0x100000001b3L;
        }
        return hash & Long.MAX_VALUE;
    }
}
