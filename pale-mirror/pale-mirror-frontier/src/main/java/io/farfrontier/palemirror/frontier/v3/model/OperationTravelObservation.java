package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Explicit provider/predecessor evidence; neither a receipt nor a scene writes HOT pose. */
public sealed interface OperationTravelObservation {
    ActorExecutionGroup executions();
    OperationTravel predecessor();

    record ColdSegment(ActorExecutionGroup executions, OperationTravel predecessor,
                       Map<SubjectId, Long> bodyEpochFences) implements OperationTravelObservation {
        public ColdSegment { requireCohort(executions, predecessor); bodyEpochFences = requireEpochs(predecessor, bodyEpochFences); }
        public static ColdSegment capture(FrontierWorldState state, RouteOperation operation) {
            return new ColdSegment(OperationExecutionAuthority.logisticsCurrent(state, operation),
                    operation.activeTravel().orElseThrow(), epochs(state, operation));
        }
    }

    /** Return to the same unfinished formation goal without route/cargo progress. */
    record ColdApproach(ActorExecutionGroup executions, OperationTravel predecessor,
                        Map<SubjectId, Long> bodyEpochFences) implements OperationTravelObservation {
        public ColdApproach { requireCohort(executions, predecessor); bodyEpochFences = requireEpochs(predecessor, bodyEpochFences); }
        public static ColdApproach capture(FrontierWorldState state, RouteOperation operation) {
            return new ColdApproach(OperationExecutionAuthority.logisticsCurrent(state, operation),
                    operation.activeTravel().orElseThrow(), epochs(state, operation));
        }
    }

    record HotSegment(ActorExecutionGroup executions, OperationTravel predecessor, SceneLeaseId leaseId,
                      Map<SubjectId, ActorHotObservation> members) implements OperationTravelObservation {
        public HotSegment {
            requireCohort(executions, predecessor);
            Objects.requireNonNull(leaseId, "observed logistics scope");
            members = Map.copyOf(members);
            if (!members.keySet().equals(predecessor.formation().keySet()))
                throw new IllegalArgumentException("logistics observation needs the complete exact crew");
            for (var execution : executions.members()) {
                if (!members.get(execution.actorId()).actuation().execution().equals(execution))
                    throw new IllegalArgumentException("logistics observation has foreign captured execution");
            }
        }
        public SceneLease require(FrontierWorldState state, RouteOperation operation, OperationTravel next) {
            var lease = state.sceneLeases().get(leaseId);
            if (lease == null || lease.status() != SceneLeaseStatus.HOT || !FrontierSceneBehaviors.isLogistics(lease)
                    || !FrontierSceneBehaviors.logistics(lease).operationId().equals(operation.id())
                    || FrontierSceneBehaviors.logistics(lease).engagementId().isPresent()
                    || !LogisticsSceneExecutionAuthority.current(state, lease).equals(executions))
                throw new IllegalArgumentException("logistics arrival lost its exact uninterrupted HOT scope");
            for (var execution : executions.members())
                members.get(execution.actorId()).require(state, execution, lease.revision(), next.formation().get(execution.actorId()));
            return lease;
        }
    }

    private static void requireCohort(ActorExecutionGroup executions, OperationTravel predecessor) {
        Objects.requireNonNull(executions); Objects.requireNonNull(predecessor);
        Set<SubjectId> members = executions.members().stream().map(id -> id.actorId()).collect(Collectors.toUnmodifiableSet());
        if (!members.equals(predecessor.formation().keySet())
                || executions.members().stream().anyMatch(id -> id.activityKind() != ActorActivityKind.LOGISTICS))
            throw new IllegalArgumentException("travel evidence must declare its exact logistics cohort");
    }

    private static Map<SubjectId, Long> requireEpochs(OperationTravel predecessor, Map<SubjectId, Long> epochs) {
        epochs = Map.copyOf(epochs);
        if (!epochs.keySet().equals(predecessor.formation().keySet()) || epochs.values().stream().anyMatch(epoch -> epoch < 1))
            throw new IllegalArgumentException("COLD travel requires the full physical epoch fence");
        return epochs;
    }
    private static Map<SubjectId, Long> epochs(FrontierWorldState state, RouteOperation operation) {
        var result = new java.util.LinkedHashMap<SubjectId, Long>();
        for (var actor : operation.participantIds()) result.put(actor,
                state.fencedRecovery().nextEpoch(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.recoveryBindingId(actor)));
        return result;
    }
    default void requireColdEpochs(FrontierWorldState state) {
        Map<SubjectId, Long> epochs = switch (this) {
            case ColdSegment cold -> cold.bodyEpochFences();
            case ColdApproach cold -> cold.bodyEpochFences();
            case HotSegment ignored -> throw new IllegalArgumentException("HOT evidence cannot authorize COLD travel");
        };
        for (var entry : epochs.entrySet()) {
            if (entry.getValue() != state.fencedRecovery().nextEpoch(
                    io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.recoveryBindingId(entry.getKey())))
                throw new IllegalArgumentException("COLD logistics receipt has stale physical history");
        }
    }
}
