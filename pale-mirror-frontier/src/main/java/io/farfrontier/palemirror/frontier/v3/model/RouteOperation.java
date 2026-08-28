package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Objects;

/** Exact cargo and named people travelling a finite COLD route until a HOT lease takes ownership. */
public record RouteOperation(SubjectId id, SubjectId settlementId, SubjectId cargoId, SubjectId destinationId,
                             List<SubjectId> participantIds, List<BlockPosition> route, int routeIndex, OperationStage stage) {
    public RouteOperation {
        Objects.requireNonNull(id); Objects.requireNonNull(settlementId); Objects.requireNonNull(cargoId); Objects.requireNonNull(destinationId);
        participantIds = List.copyOf(participantIds); route = List.copyOf(route); Objects.requireNonNull(stage);
        if (participantIds.isEmpty() || participantIds.size() > 8 || participantIds.stream().distinct().count() != participantIds.size()) throw new IllegalArgumentException("operation participants must be one to eight distinct residents");
        if (route.size() < 2 || route.size() > 128) throw new IllegalArgumentException("operation route must contain 2..128 positions");
        if (routeIndex < 0 || routeIndex >= route.size()) throw new IllegalArgumentException("operation route index is outside route");
        if (stage == OperationStage.ASSEMBLING && routeIndex != 0) throw new IllegalArgumentException("assembling operation must begin at its first route point");
        if (stage == OperationStage.EN_ROUTE && routeIndex == route.size() - 1) throw new IllegalArgumentException("en-route operation cannot already be at its final route point");
        if (stage == OperationStage.ARRIVED && routeIndex != route.size() - 1) throw new IllegalArgumentException("arrived operation must be at final route point");
    }
}
