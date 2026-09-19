package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Exact cargo and named people travelling a finite COLD route until a HOT lease takes ownership. */
public record RouteOperation(SubjectId id, SubjectId contractId, SubjectId settlementId, SubjectId cargoId, SubjectId destinationId,
                             RouteUnitManifest unit, List<BlockPosition> route, int routeIndex, OperationStage stage,
                             Optional<OperationAssembly> activeAssembly, Optional<OperationTravel> activeTravel, TacticalPlan tacticalPlan) {
    RouteOperation(SubjectId id, SubjectId contractId, SubjectId settlementId, SubjectId cargoId, SubjectId destinationId,
                   List<SubjectId> participantIds, List<BlockPosition> route, int routeIndex, OperationStage stage) {
        this(id, contractId, settlementId, cargoId, destinationId, legacyUnit(id, participantIds), route, routeIndex, stage, Optional.empty(), Optional.empty(),
                TacticalPlan.cargoEscort(id, settlementId, 0L, legacyUnit(id, participantIds), route));
    }
    RouteOperation(SubjectId id, SubjectId contractId, SubjectId settlementId, SubjectId cargoId, SubjectId destinationId,
                   List<SubjectId> participantIds, List<BlockPosition> route, int routeIndex, OperationStage stage,
                   Optional<OperationTravel> activeTravel) {
        this(id, contractId, settlementId, cargoId, destinationId, legacyUnit(id, participantIds), route, routeIndex, stage, Optional.empty(), activeTravel,
                TacticalPlan.cargoEscort(id, settlementId, 0L, legacyUnit(id, participantIds), route));
    }
    /** Historical constructor retained only for schema/WAL hydration callers. */
    RouteOperation(SubjectId id, SubjectId contractId, SubjectId settlementId, SubjectId cargoId, SubjectId destinationId,
                   List<SubjectId> participantIds, List<BlockPosition> route, int routeIndex, OperationStage stage,
                   Optional<OperationAssembly> activeAssembly, Optional<OperationTravel> activeTravel) {
        this(id, contractId, settlementId, cargoId, destinationId, legacyUnit(id, participantIds), route, routeIndex, stage, activeAssembly, activeTravel,
                TacticalPlan.cargoEscort(id, settlementId, 0L, legacyUnit(id, participantIds), route));
    }
    public RouteOperation(SubjectId id, SubjectId contractId, SubjectId settlementId, SubjectId cargoId, SubjectId destinationId,
                          RouteUnitManifest unit, List<BlockPosition> route, int routeIndex, OperationStage stage,
                          Optional<OperationAssembly> activeAssembly, Optional<OperationTravel> activeTravel) {
        this(id, contractId, settlementId, cargoId, destinationId, unit, route, routeIndex, stage, activeAssembly, activeTravel,
                TacticalPlan.cargoEscort(id, settlementId, 0L, unit, route));
    }
    public RouteOperation {
        Objects.requireNonNull(id); Objects.requireNonNull(contractId, "route operation contract"); Objects.requireNonNull(settlementId); Objects.requireNonNull(cargoId); Objects.requireNonNull(destinationId);
        Objects.requireNonNull(unit, "route operation unit"); route = List.copyOf(route); Objects.requireNonNull(stage);
        activeAssembly = Objects.requireNonNull(activeAssembly, "active operation assembly");
        activeTravel = Objects.requireNonNull(activeTravel, "active operation travel");
        tacticalPlan = Objects.requireNonNull(tacticalPlan, "operation tactical plan");
        if (unit.kind() != RouteUnitKind.CARGO_ESCORT || !unit.ownerId().equals(id) || !unit.id().equals(RouteUnitManifest.idFor(RouteUnitKind.CARGO_ESCORT, id))) {
            throw new IllegalArgumentException("route operation must own its exact cargo unit");
        }
        if (!tacticalPlan.operationId().equals(id) || !tacticalPlan.authorityId().equals(settlementId)
                || !tacticalPlan.policy().equals(TacticalPolicyRegistry.CARGO_ESCORT)) {
            throw new IllegalArgumentException("route operation must retain its canonical tactical plan");
        }
        TacticalPolicyRegistry.require(tacticalPlan.policy());
        tacticalPlan.validateMembers(unit.memberIds());
        if (route.size() < 2 || route.size() > 128) throw new IllegalArgumentException("operation route must contain 2..128 positions");
        if (routeIndex < 0 || routeIndex >= route.size()) throw new IllegalArgumentException("operation route index is outside route");
        if (stage == OperationStage.ASSEMBLING && routeIndex != 0) throw new IllegalArgumentException("assembling operation must begin at its first route point");
        if (stage == OperationStage.EN_ROUTE && routeIndex == route.size() - 1) throw new IllegalArgumentException("en-route operation cannot already be at its final route point");
        if (stage == OperationStage.ARRIVED && routeIndex != route.size() - 1) throw new IllegalArgumentException("arrived operation must be at final route point");
        if (stage == OperationStage.RETURNING && routeIndex == 0) throw new IllegalArgumentException("returning operation cannot already be home");
        if (stage == OperationStage.COMPLETED && routeIndex != 0) throw new IllegalArgumentException("completed operation must be home");
        if (stage == OperationStage.ASSEMBLING && (activeAssembly.isEmpty() || activeTravel.isPresent())) throw new IllegalArgumentException("assembling operation needs one assembly and no travel");
        if (stage != OperationStage.ASSEMBLING && activeAssembly.isPresent()) throw new IllegalArgumentException("only assembling operation owns assembly");
        if (activeAssembly.isPresent() && !activeAssembly.orElseThrow().members().keySet().equals(java.util.Set.copyOf(unit.memberIds()))) throw new IllegalArgumentException("assembly membership differs from operation formation");
        if (activeTravel.isPresent()) {
            OperationTravel travel = activeTravel.orElseThrow();
            int next = stage == OperationStage.RETURNING ? routeIndex - 1 : routeIndex + 1;
            boolean moving = (stage == OperationStage.EN_ROUTE && routeIndex < route.size() - 1) || (stage == OperationStage.RETURNING && routeIndex > 0);
            boolean inProgress = moving && !travel.arrived() && travel.corridor().getFirst().equals(route.get(routeIndex)) && travel.corridor().getLast().equals(route.get(next));
            boolean arrivedAwaitingHandOff = moving && travel.arrived() && travel.corridor().getFirst().equals(route.get(routeIndex)) && travel.corridor().getLast().equals(route.get(next));
            boolean completed = travel.arrived() && travel.corridor().getLast().equals(route.get(routeIndex));
            if ((!inProgress && !arrivedAwaitingHandOff && !completed) || !travel.formation().keySet().equals(java.util.Set.copyOf(unit.memberIds()))) {
                throw new IllegalArgumentException("operation travel must exactly own the next strategic segment and formation");
            }
        }
    }

    public List<SubjectId> participantIds() { return unit.memberIds(); }

    public BlockPosition currentPosition() {
        if (activeTravel.isPresent()) return activeTravel.orElseThrow().currentPosition();
        return route.get(routeIndex);
    }

    /**
     * True only while this operation owns a nonterminal exact corridor segment.
     *
     * <p>An arrived segment is deliberately retained until the deterministic COLD process
     * opens the next segment atomically.  A HOT executor must not take that transient state:
     * doing so would suspend the very COLD action that advances the route and could materialize
     * a stale formation at the previous milestone.</p>
     */
    public boolean hasInProgressTravel() {
        return (stage == OperationStage.EN_ROUTE || stage == OperationStage.RETURNING) && activeTravel.filter(travel -> !travel.arrived()).isPresent();
    }

    /** Supply operations persist the hauler first; assembly makes the same identity explicit before departure. */
    public SubjectId cargoCarrierId() { return activeAssembly.map(OperationAssembly::cargoCarrierId).orElseGet(unit::cargoCrewId); }

    public RouteOperation withAssembly(OperationAssembly assembly) {
        return new RouteOperation(id, contractId, settlementId, cargoId, destinationId, unit, route, routeIndex, stage, Optional.of(assembly), activeTravel, tacticalPlan);
    }

    public RouteOperation startTravel(OperationTravel travel) {
        Objects.requireNonNull(travel, "operation travel");
        if (activeAssembly.isPresent()) {
            OperationAssembly assembly = activeAssembly.orElseThrow();
            if (!assembly.complete() || !travel.formation().equals(bodyPositions(assembly.positions()))
                    || !travel.cargoAnchor().equals(assembly.cargoAnchor())) {
                throw new IllegalArgumentException("operation travel must preserve its complete assembly formation and cargo anchor");
            }
            return new RouteOperation(id, contractId, settlementId, cargoId, destinationId, unit, route, routeIndex, OperationStage.EN_ROUTE, Optional.empty(), Optional.of(travel), tacticalPlan.withPhase(TacticalPlanPhase.TRAVEL));
        }
        OperationTravel previous = activeTravel.orElseThrow(() -> new IllegalArgumentException("operation has no completed travel formation"));
        if (!previous.arrived() || !previous.formation().equals(travel.formation()) || !previous.cargoAnchor().equals(travel.cargoAnchor())) {
            throw new IllegalArgumentException("next operation travel must retain the arrived formation and cargo anchor");
        }
        OperationStage nextStage = stage == OperationStage.ARRIVED || stage == OperationStage.RETURNING ? OperationStage.RETURNING : OperationStage.EN_ROUTE;
        TacticalPlanPhase phase = nextStage == OperationStage.RETURNING ? TacticalPlanPhase.RETURN : TacticalPlanPhase.TRAVEL;
        return new RouteOperation(id, contractId, settlementId, cargoId, destinationId, unit, route, routeIndex, nextStage,
                Optional.empty(), Optional.of(travel), tacticalPlan.withPhase(phase));
    }

    public RouteOperation withTravel(OperationTravel travel) {
        return new RouteOperation(id, contractId, settlementId, cargoId, destinationId, unit, route, routeIndex, stage, activeAssembly, Optional.of(travel), tacticalPlan);
    }

    /** Applies loaded owned-route loss to this operation's retained segment only. */
    public RouteOperation blockTravelAt(BlockPosition physicalSurface) {
        Objects.requireNonNull(physicalSurface, "physical route surface");
        if (activeTravel.isEmpty()) return this;
        OperationTravel travel = activeTravel.orElseThrow();
        java.util.Set<TraversalEdgeId> affected = FrontierRouteNetwork.affectedTraversalEdges(travel.topology(), physicalSurface);
        if (affected.isEmpty()) return this;
        return withTravel(travel.withAvailability(affected, TraversalAvailability.BLOCKED));
    }

    public RouteOperation completeTravelSegment() {
        OperationTravel travel = activeTravel.orElseThrow(() -> new IllegalArgumentException("operation has no exact travel to complete"));
        if (!travel.arrived()) throw new IllegalArgumentException("operation travel segment has not arrived");
        int nextIndex = stage == OperationStage.RETURNING ? routeIndex - 1 : routeIndex + 1;
        OperationStage nextStage = stage == OperationStage.RETURNING ? (nextIndex == 0 ? OperationStage.COMPLETED : OperationStage.RETURNING)
                : (nextIndex == route.size() - 1 ? OperationStage.ARRIVED : OperationStage.EN_ROUTE);
        return new RouteOperation(id, contractId, settlementId, cargoId, destinationId, unit, route, nextIndex, nextStage, Optional.empty(), Optional.of(travel),
                tacticalPlan.withPhase(nextStage == OperationStage.COMPLETED ? TacticalPlanPhase.COMPLETE : nextStage == OperationStage.ARRIVED ? TacticalPlanPhase.CONTACT : tacticalPlan.phase()));
    }

    private static RouteUnitManifest legacyUnit(SubjectId id, List<SubjectId> participantIds) {
        List<SubjectId> copied = List.copyOf(participantIds);
        if (copied.size() != 2) throw new IllegalArgumentException("legacy route operation must retain one crew member and one guard");
        return RouteUnitManifest.legacyCargoEscort(id, copied.getFirst(), copied.get(1));
    }

    private static java.util.Map<SubjectId, BodyPosition> bodyPositions(java.util.Map<SubjectId, SurfaceAnchor> supports) {
        java.util.Map<SubjectId, BodyPosition> bodies = new java.util.LinkedHashMap<>();
        supports.forEach((actor, support) -> bodies.put(actor, BodyPosition.above(support)));
        return java.util.Map.copyOf(bodies);
    }
}
