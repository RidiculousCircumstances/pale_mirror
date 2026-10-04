package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Logistics owns crew-purpose declarations; cargo custody remains outside this boundary. */
public final class OperationExecutionAuthority {
    private OperationExecutionAuthority() { }
    public static ActorExecutionGroup assemblyAdmission(FrontierWorldState state, RouteOperation operation) {
        return new ActorExecutionGroup(operation.participantIds().stream().map(actor ->
                state.actorExecutions().next(actor, ActorActivityKind.OPERATION_ASSEMBLY, operation.id())).toList());
    }
    public static ActorExecutionGroup assemblyCurrent(FrontierWorldState state, RouteOperation operation) {
        return current(state, operation, ActorActivityKind.OPERATION_ASSEMBLY);
    }
    /** Resolve the retained nominal owner; roster membership validates it, never discovers it. */
    public static Optional<RouteOperation> assemblyOwner(FrontierWorldState state, SubjectId actor) {
        var execution = state.actorExecutions().current(ActorActivityKind.OPERATION_ASSEMBLY).get(actor);
        if (execution == null) return Optional.empty();
        state.actorExecutions().requireCurrent(execution);
        return Optional.of(require(state.operations(), execution, ActorActivityKind.OPERATION_ASSEMBLY,
                operation -> operation.stage() == OperationStage.ASSEMBLING));
    }
    public static ActorExecutionGroup logisticsCurrent(FrontierWorldState state, RouteOperation operation) {
        return current(state, operation, ActorActivityKind.LOGISTICS);
    }
    public static ActorExecutionGroup logisticsAdmission(FrontierWorldState state, RouteOperation operation) {
        return new ActorExecutionGroup(operation.participantIds().stream().map(actor ->
                state.actorExecutions().next(actor, ActorActivityKind.LOGISTICS, operation.id())).toList());
    }
    /** The owner declares its Hall passage; shared knowledge still owns all obstacle rules. */
    private static KnownPedestrianRouteKnowledge assemblyKnowledge(FrontierWorldState state, RouteOperation operation) {
        return OperationAssemblyCorridor.knowledge(state, operation.settlementId());
    }
    /** Recheck every retained edge, including a rejoin, before any background pose commit. */
    public static boolean coldAssemblyTraversalAvailable(FrontierWorldState state, RouteOperation operation,
                                                          OperationAssembly.Member before, OperationAssembly.Member after) {
        var knowledge = assemblyKnowledge(state, operation);
        var cursor = before;
        if (!knowledge.traversable(java.util.List.of(cursor.currentSurface()))) return false;
        for (int step = 0; step < OperationAssembly.MAX_COLD_ADVANCE && !cursor.arrived(); step++) {
            cursor = cursor.advanceOne();
            if (!knowledge.traversable(java.util.List.of(cursor.currentSurface()))) return false;
            if (cursor.equals(after)) return true;
        }
        return false;
    }
    public static OperationTravelStarted travelStarted(FrontierWorldState state, RouteOperation operation, OperationTravel travel) {
        return new OperationTravelStarted(operation.id(), travel, operation.stage() == OperationStage.ASSEMBLING
                ? logisticsAdmission(state, operation) : logisticsCurrent(state, operation));
    }
    public static ActorExecutionState retired(FrontierWorldState state, ActorExecutionGroup executions) {
        executions.requireCurrent(state.actorExecutions());
        var result = state.actorExecutions();
        for (var id : executions.members())
            result = ActorExecutionComposition.LIFECYCLE.retire(result, id.actorId(), id.activityKind(), id.activityOwnerId());
        return result;
    }
    private static ActorExecutionGroup current(FrontierWorldState state, RouteOperation operation, ActorActivityKind kind) {
        return new ActorExecutionGroup(operation.participantIds().stream().map(actor -> {
            var key = state.actorExecutions().current(kind).get(actor);
            if (key == null || !key.activityOwnerId().equals(operation.id()))
                throw new IllegalArgumentException("operation has no exact current participant execution");
            state.actorExecutions().requireCurrent(key);
            return key;
        }).toList());
    }
    static void validateReferences(Map<SubjectId, RouteOperation> operations, ActorExecutionState executions) {
        for (var id : executions.current(ActorActivityKind.OPERATION_ASSEMBLY).values())
            require(operations, id, ActorActivityKind.OPERATION_ASSEMBLY, operation -> operation.stage() == OperationStage.ASSEMBLING);
        for (var id : executions.current(ActorActivityKind.LOGISTICS).values())
            require(operations, id, ActorActivityKind.LOGISTICS, OperationExecutionAuthority::activeTravelPurpose);
        for (var operation : operations.values()) {
            if (operation.stage() != OperationStage.ASSEMBLING && !activeTravelPurpose(operation)) continue;
            for (var actor : operation.participantIds()) {
                var retained = executions.actors().get(actor);
                var id = retained == null ? null : retained.current().orElse(null);
                if (id == null || !id.activityOwnerId().equals(operation.id())
                        || !(id.activityKind() == ActorActivityKind.OPERATION_ASSEMBLY && operation.stage() == OperationStage.ASSEMBLING
                        || id.activityKind() == ActorActivityKind.LOGISTICS && activeTravelPurpose(operation)))
                    throw new IllegalArgumentException("active operation lost an explicitly declared participant execution");
            }
        }
    }
    private static RouteOperation require(Map<SubjectId, RouteOperation> operations, ActorExecutionId id,
                                          ActorActivityKind kind, Predicate<RouteOperation> phase) {
        var operation = operations.get(id.activityOwnerId());
        if (id.activityKind() != kind || operation == null || !operation.participantIds().contains(id.actorId())
                || !phase.test(operation)) throw new IllegalArgumentException("operation execution lost its declared crew purpose");
        return operation;
    }
    static ActorActivityCapability assemblyCapability() {
        return capability(ActorActivityKind.OPERATION_ASSEMBLY, operation -> operation.stage() == OperationStage.ASSEMBLING,
                operation -> operation.activeAssembly().orElseThrow().complete());
    }
    static ActorActivityCapability logisticsCapability() {
        return capability(ActorActivityKind.LOGISTICS, OperationExecutionAuthority::activeTravelPurpose,
                operation -> operation.stage() == OperationStage.COMPLETED);
    }
    private static ActorActivityCapability capability(ActorActivityKind kind, Predicate<RouteOperation> phase,
                                                      Predicate<RouteOperation> terminal) {
        Objects.requireNonNull(kind); Objects.requireNonNull(phase); Objects.requireNonNull(terminal);
        return new ActorActivityCapability() {
            @Override public ActorActivityBodyCheckpoint bodyCheckpoint() {
                return request -> {
                    var state = request.expectedState();
                    var id = request.execution();
                    var operation = require(state.operations(), id, kind, phase);
                    if (kind == ActorActivityKind.OPERATION_ASSEMBLY) {
                        var assembly = operation.activeAssembly().orElseThrow();
                        var member = assembly.members().get(id.actorId());
                        var start = request.observedPosition().supportingSurface();
                        if (member.currentSurface().equals(start))
                            return new ActorActivityBodyCheckpoint.Acknowledgement(request, FrontierWorldStateUpdate.begin());
                        if (assembly.deferral().isPresent()) throw new IllegalArgumentException("observed assembly obstruction must settle before rejoin");
                        var target = member.arrived() ? member.currentSurface() : member.corridor().get(member.cursor() + 1);
                        var order = new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(operation.id(), id.actorId(),
                                member.cursor(), Math.incrementExact(member.routeRevision()), java.util.List.of(target), TraversalCapability.PEDESTRIAN,
                                io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION);
                        var path = assemblyKnowledge(state, operation).path(start, order);
                        var members = new java.util.LinkedHashMap<>(assembly.members());
                        members.put(id.actorId(), member.withRejoin(new io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin(path, 0)));
                        var operations = new java.util.LinkedHashMap<>(state.operations());
                        operations.put(operation.id(), operation.withAssembly(new OperationAssembly(members, assembly.cargoCarrierId())));
                        return new ActorActivityBodyCheckpoint.Acknowledgement(request, FrontierWorldStateUpdate.begin().operations(operations));
                    }
                    if (operation.activeTravel().isEmpty()) {
                        if (!operation.currentPosition().equals(request.observedPosition().supportingSurface().support()))
                            throw new IllegalArgumentException("operation requires its observed strategic checkpoint before COLD");
                        return new ActorActivityBodyCheckpoint.Acknowledgement(request, FrontierWorldStateUpdate.begin());
                    }
                    var travel = operation.activeTravel().orElseThrow();
                    if (travel.memberCheckpoint(id.actorId()).equals(request.observedPosition()))
                        return new ActorActivityBodyCheckpoint.Acknowledgement(request, FrontierWorldStateUpdate.begin());
                    var checkpoint = travel.checkpointMember(id.actorId(), OperationTravelContinuation.checkpoint(state,
                            operation, id.actorId(), request.observedPosition().supportingSurface()));
                    var update = FrontierWorldStateUpdate.begin();
                    if (checkpoint != travel) {
                        var operations = new java.util.LinkedHashMap<>(state.operations());
                        operations.put(operation.id(), operation.withTravel(checkpoint));
                        update.operations(operations);
                    }
                    return new ActorActivityBodyCheckpoint.Acknowledgement(request, update);
                };
            }
            @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId id, AmbientActorLease lease) {
                if (kind != ActorActivityKind.OPERATION_ASSEMBLY || lease.goal() != AmbientGoalKind.OPERATION_ASSEMBLY) return false;
                var operation = require(state.operations(), id, kind, phase);
                var member = operation.activeAssembly().orElseThrow().members().get(id.actorId());
                var target = member.arrived() ? member.currentSurface() : member.nextSurface();
                return lease.goalBody().supportingSurface().equals(target);
            }
            @Override public ActorActivityKind kind() { return kind; }
            @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) { }
            @Override public Interruption interruption() { return Interruption.TERMINAL_ONLY; }
            @Override public void validateReference(FrontierWorldState state, ActorExecutionId id) {
                require(state.operations(), id, kind, phase);
            }
            @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId id) {
                var operation = require(state.operations(), id, kind, phase);
                return new ActorActivityCheckpoint(state, id, terminal.test(operation) ? Optional.empty() : Optional.of(
                        new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, operation.id())));
            }
            @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId id, long atTick) {
                throw new IllegalArgumentException("operation crew must reach its owner terminal boundary");
            }
            @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId id, long atTick) {
                throw new IllegalArgumentException("operation crew has no suspended activity");
            }
            @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId id) {
                throw new IllegalArgumentException("operation crew retires through its exact owner");
            }
        };
    }
    private static boolean activeTravelPurpose(RouteOperation operation) {
        return operation.stage() == OperationStage.EN_ROUTE || operation.stage() == OperationStage.ARRIVED
                || operation.stage() == OperationStage.RETURNING;
    }
}
