package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Objects;

/** State mutation contract for failure of an exact en-route operation. */
final class FrontierOperationStateSupport {
    private FrontierOperationStateSupport() { }

    static FrontierWorldState fail(FrontierWorldState state, SubjectId operationId) {
        RouteOperation current = state.operations().get(Objects.requireNonNull(operationId, "operation id"));
        if (current == null || current.stage() != OperationStage.EN_ROUTE) throw new IllegalArgumentException("only an en-route operation can fail");
        if (state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isLogistics).anyMatch(lease -> FrontierSceneBehaviors.logistics(lease).operationId().equals(operationId) && lease.status() != SceneLeaseStatus.CLOSED
                && !(lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART && lease.recoveryEvidence().isPresent()))) {
            throw new IllegalArgumentException("operation cannot fail before its active scene lease closes");
        }
        var operations = new LinkedHashMap<>(state.operations());
        operations.put(operationId, new RouteOperation(current.id(), current.settlementId(), current.cargoId(), current.destinationId(),
                current.participantIds(), current.route(), current.routeIndex(), OperationStage.FAILED));
        return state.next(state.actorLocations(), state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(), state.contracts(), operations,
                state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(), state.structureDamage(), state.physicalDeltas(), state.ambientLeases());
    }
}
