package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.OperationTravel;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test-only lease fixture that copies the current canonical operation positions exactly. */
final class FrontierV3TestSceneLeases {
    private FrontierV3TestSceneLeases() { }

    static SceneLease exact(FrontierWorldState state, CheckpointImage checkpoint, SceneLeaseId id, SubjectId operationId,
                            SubjectId cargoId, BlockPosition demand, Optional<SubjectId> engagementId, List<SubjectId> actorIds) {
        RouteOperation operation = state.operations().get(operationId);
        if (operation == null) throw new IllegalArgumentException("test scene operation is not canonical: " + operationId.value());
        Map<SubjectId, BodyPosition> positions = new LinkedHashMap<>();
        List<SceneMember> members = actorIds.stream().sorted().map(actor -> {
            var location = state.actorLocations().get(actor);
            if (location == null) throw new IllegalArgumentException("test scene actor is not canonical: " + actor.value());
            positions.put(actor, location.body());
            return new SceneMember(actor, SceneLease.deterministicEntityId(checkpoint.worldId(), actor));
        }).toList();
        BlockPosition cargo = operation.activeTravel().map(travel -> travel.cargoAnchor().surface().support()).orElse(demand);
        return SceneLease.atExactPositions(id, checkpoint.worldId(), operationId, cargoId, demand, cargo,
                checkpoint.instant(), checkpoint.revision().value(), SceneLeaseStatus.PREPARED, engagementId, members, positions);
    }
}
