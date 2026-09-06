package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.OperationTravel;
import io.farfrontier.palemirror.frontier.v3.model.RouteOperation;
import io.farfrontier.palemirror.frontier.v3.model.SceneEngagementCandidate;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SceneLeaseStatus;
import io.farfrontier.palemirror.frontier.v3.model.SceneMember;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Exact canonical scene leases for NeoForge GameTests; never invents a local test formation. */
final class FrontierV3GameTestSceneLeases {
    private FrontierV3GameTestSceneLeases() { }

    static SceneLease exact(FrontierWorldState state, CheckpointImage checkpoint, SceneEngagementCandidate candidate, SceneLeaseId id) {
        RouteOperation operation = state.operations().get(candidate.operationId());
        if (operation == null) throw new IllegalStateException("GameTest scene candidate has no operation");
        BlockPosition cargoPosition = operation.activeTravel().map(travel -> travel.cargoAnchor().surface().support())
                .orElseThrow(() -> new IllegalStateException("GameTest scene operation has no exact cargo anchor"));
        List<SceneMember> members = candidate.actorIds().stream()
                .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(checkpoint.worldId(), actor))).toList();
        Map<SubjectId, BodyPosition> positions = new LinkedHashMap<>();
        for (SceneMember member : members) positions.put(member.actorId(), state.actorLocations().get(member.actorId()).body());
        return SceneLease.atExactPositions(id, checkpoint.worldId(), candidate.operationId(), candidate.cargoId(), candidate.handoffPosition(),
                cargoPosition, checkpoint.instant(), checkpoint.revision().value(), SceneLeaseStatus.PREPARED,
                Optional.of(candidate.engagementId()), members, positions);
    }

    /**
     * GameTest templates are intentionally placed outside Frontier's finite canonical bounds.
     * This pilot-only projection keeps the one canonical lease ID/member IDs/UUIDs untouched
     * while translating only its observed body cells into the naturally loaded template.  It is
     * not a production coordinate conversion and is never submitted back to the canonical lane.
     */
    static SceneLease projectedIntoFixture(SceneLease canonical, BodyPosition firstBody) {
        Objects.requireNonNull(canonical, "canonical scene lease"); Objects.requireNonNull(firstBody, "fixture first body");
        SceneMember first = canonical.members().getFirst(); BodyPosition origin = canonical.memberPosition(first.actorId());
        int dx = firstBody.x() - origin.x(), dy = firstBody.y() - origin.y(), dz = firstBody.z() - origin.z();
        Map<SubjectId, BodyPosition> translated = new LinkedHashMap<>();
        for (SceneMember member : canonical.members()) {
            BodyPosition body = canonical.memberPosition(member.actorId());
            translated.put(member.actorId(), new BodyPosition(body.x() + dx, body.y() + dy, body.z() + dz));
        }
        return canonical.withMemberPositions(translated);
    }
}
