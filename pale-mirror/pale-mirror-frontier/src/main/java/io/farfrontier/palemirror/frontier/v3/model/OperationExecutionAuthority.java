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
    public static ActorExecutionGroup logisticsCurrent(FrontierWorldState state, RouteOperation operation) {
        return current(state, operation, ActorActivityKind.LOGISTICS);
    }
    public static ActorExecutionGroup logisticsAdmission(FrontierWorldState state, RouteOperation operation) {
        return new ActorExecutionGroup(operation.participantIds().stream().map(actor ->
                state.actorExecutions().next(actor, ActorActivityKind.LOGISTICS, operation.id())).toList());
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
            @Override public ActorActivityKind kind() { return kind; }
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
