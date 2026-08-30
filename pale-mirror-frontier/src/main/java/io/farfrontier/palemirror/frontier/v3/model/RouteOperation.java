package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Exact cargo and named people travelling a finite COLD route until a HOT lease takes ownership. */
public record RouteOperation(SubjectId id, SubjectId settlementId, SubjectId cargoId, SubjectId destinationId,
                             List<SubjectId> participantIds, List<BlockPosition> route, int routeIndex, OperationStage stage,
                             Optional<OperationAssembly> activeAssembly, Optional<OperationTravel> activeTravel) {
    public RouteOperation(SubjectId id, SubjectId settlementId, SubjectId cargoId, SubjectId destinationId,
                          List<SubjectId> participantIds, List<BlockPosition> route, int routeIndex, OperationStage stage) {
        this(id, settlementId, cargoId, destinationId, participantIds, route, routeIndex, stage, Optional.empty(), Optional.empty());
    }
    public RouteOperation(SubjectId id, SubjectId settlementId, SubjectId cargoId, SubjectId destinationId,
                          List<SubjectId> participantIds, List<BlockPosition> route, int routeIndex, OperationStage stage,
                          Optional<OperationTravel> activeTravel) {
        this(id, settlementId, cargoId, destinationId, participantIds, route, routeIndex, stage, Optional.empty(), activeTravel);
    }
    public RouteOperation {
        Objects.requireNonNull(id); Objects.requireNonNull(settlementId); Objects.requireNonNull(cargoId); Objects.requireNonNull(destinationId);
        participantIds = List.copyOf(participantIds); route = List.copyOf(route); Objects.requireNonNull(stage);
        activeAssembly = Objects.requireNonNull(activeAssembly, "active operation assembly");
        activeTravel = Objects.requireNonNull(activeTravel, "active operation travel");
        if (participantIds.isEmpty() || participantIds.size() > 8 || participantIds.stream().distinct().count() != participantIds.size()) throw new IllegalArgumentException("operation participants must be one to eight distinct residents");
        if (route.size() < 2 || route.size() > 128) throw new IllegalArgumentException("operation route must contain 2..128 positions");
        if (routeIndex < 0 || routeIndex >= route.size()) throw new IllegalArgumentException("operation route index is outside route");
        if (stage == OperationStage.ASSEMBLING && routeIndex != 0) throw new IllegalArgumentException("assembling operation must begin at its first route point");
        if (stage == OperationStage.EN_ROUTE && routeIndex == route.size() - 1) throw new IllegalArgumentException("en-route operation cannot already be at its final route point");
        if (stage == OperationStage.ARRIVED && routeIndex != route.size() - 1) throw new IllegalArgumentException("arrived operation must be at final route point");
        if (stage == OperationStage.ASSEMBLING && (activeAssembly.isEmpty() || activeTravel.isPresent())) throw new IllegalArgumentException("assembling operation needs one assembly and no travel");
        if (stage != OperationStage.ASSEMBLING && activeAssembly.isPresent()) throw new IllegalArgumentException("only assembling operation owns assembly");
        if (activeAssembly.isPresent() && !activeAssembly.orElseThrow().members().keySet().equals(java.util.Set.copyOf(participantIds))) throw new IllegalArgumentException("assembly membership differs from operation formation");
        if (activeTravel.isPresent()) {
            OperationTravel travel = activeTravel.orElseThrow();
            boolean inProgress = stage == OperationStage.EN_ROUTE && routeIndex < route.size() - 1 && !travel.arrived()
                    && travel.corridor().getFirst().equals(route.get(routeIndex)) && travel.corridor().getLast().equals(route.get(routeIndex + 1));
            boolean arrivedAwaitingHandOff = stage == OperationStage.EN_ROUTE && routeIndex < route.size() - 1 && travel.arrived()
                    && travel.corridor().getFirst().equals(route.get(routeIndex)) && travel.corridor().getLast().equals(route.get(routeIndex + 1));
            boolean completed = travel.arrived() && travel.corridor().getLast().equals(route.get(routeIndex));
            if ((!inProgress && !arrivedAwaitingHandOff && !completed) || !travel.formation().keySet().equals(java.util.Set.copyOf(participantIds))) {
                throw new IllegalArgumentException("operation travel must exactly own the next strategic segment and formation");
            }
        }
    }

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
        return stage == OperationStage.EN_ROUTE && activeTravel.filter(travel -> !travel.arrived()).isPresent();
    }

    /** Supply operations persist the hauler first; assembly makes the same identity explicit before departure. */
    public SubjectId cargoCarrierId() { return activeAssembly.map(OperationAssembly::cargoCarrierId).orElseGet(participantIds::getFirst); }

    public RouteOperation withAssembly(OperationAssembly assembly) {
        return new RouteOperation(id, settlementId, cargoId, destinationId, participantIds, route, routeIndex, stage, Optional.of(assembly), activeTravel);
    }

    public RouteOperation startTravel(OperationTravel travel) {
        Objects.requireNonNull(travel, "operation travel");
        if (activeAssembly.isPresent()) {
            OperationAssembly assembly = activeAssembly.orElseThrow();
            if (!assembly.complete() || !travel.formation().equals(assembly.positions())
                    || !travel.cargoAnchor().equals(assembly.cargoAnchor())) {
                throw new IllegalArgumentException("operation travel must preserve its complete assembly formation and cargo anchor");
            }
            return new RouteOperation(id, settlementId, cargoId, destinationId, participantIds, route, routeIndex, OperationStage.EN_ROUTE, Optional.empty(), Optional.of(travel));
        }
        OperationTravel previous = activeTravel.orElseThrow(() -> new IllegalArgumentException("operation has no completed travel formation"));
        if (!previous.arrived() || !previous.formation().equals(travel.formation()) || !previous.cargoAnchor().equals(travel.cargoAnchor())) {
            throw new IllegalArgumentException("next operation travel must retain the arrived formation and cargo anchor");
        }
        return new RouteOperation(id, settlementId, cargoId, destinationId, participantIds, route, routeIndex, OperationStage.EN_ROUTE, Optional.empty(), Optional.of(travel));
    }

    public RouteOperation withTravel(OperationTravel travel) {
        return new RouteOperation(id, settlementId, cargoId, destinationId, participantIds, route, routeIndex, stage, activeAssembly, Optional.of(travel));
    }

    public RouteOperation completeTravelSegment() {
        OperationTravel travel = activeTravel.orElseThrow(() -> new IllegalArgumentException("operation has no exact travel to complete"));
        if (!travel.arrived()) throw new IllegalArgumentException("operation travel segment has not arrived");
        int nextIndex = routeIndex + 1;
        OperationStage nextStage = nextIndex == route.size() - 1 ? OperationStage.ARRIVED : OperationStage.EN_ROUTE;
        return new RouteOperation(id, settlementId, cargoId, destinationId, participantIds, route, nextIndex, nextStage, Optional.empty(), Optional.of(travel));
    }
}
