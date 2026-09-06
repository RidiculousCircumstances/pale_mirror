package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test-only exact lease fixture; mirrors the production state-to-lease boundary. */
final class FrontierTestSceneLeases {
    private FrontierTestSceneLeases() { }

    static SceneLease exact(FrontierWorldState state, SceneLeaseId id, SubjectId operationId, SubjectId cargoId,
                            BlockPosition demand, SimInstant instant, long revision, Optional<SubjectId> engagementId,
                            List<SubjectId> actorIds) {
        Map<SubjectId, BlockPosition> positions = new LinkedHashMap<>();
        List<SceneMember> members = actorIds.stream().sorted().map(actor -> {
            ActorLocation location = state.actorLocations().get(actor);
            if (location == null) throw new IllegalArgumentException("test scene actor is not canonical: " + actor.value());
            positions.put(actor, FrontierTestPositions.supportOf(location));
            return new SceneMember(actor, SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor));
        }).toList();
        RouteOperation operation = state.operations().get(operationId);
        BlockPosition cargoPosition = operation == null ? demand
                : operation.activeTravel().map(travel -> travel.cargoAnchor().surface().support()).orElse(demand);
        return SceneLease.atExactPositions(id, state.bootstrap().worldId(), operationId, cargoId, demand,
                cargoPosition, instant, revision, SceneLeaseStatus.PREPARED, engagementId, members,
                SceneLease.bodiesAboveSupportCells(positions));
    }
}
