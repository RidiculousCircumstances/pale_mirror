package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
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


    /**
     * GameTest templates are intentionally placed outside Frontier's finite canonical bounds.
     * This pilot-only projection keeps the one canonical lease ID/member IDs/UUIDs untouched
     * while translating only its observed body cells into the naturally loaded template.  It is
     * not a production coordinate conversion and is never submitted back to the canonical lane.
     */
    static Map<SubjectId, BodyPosition> projectedIntoFixture(FrontierWorldState state, SceneLease canonical, BodyPosition firstBody) {
        Objects.requireNonNull(canonical, "canonical scene lease"); Objects.requireNonNull(firstBody, "fixture first body");
        SceneMember first = canonical.members().getFirst(); BodyPosition origin = canonical.memberBody(state.actorLocations(), first.actorId());
        int dx = firstBody.x() - origin.x(), dy = firstBody.y() - origin.y(), dz = firstBody.z() - origin.z();
        Map<SubjectId, BodyPosition> translated = new LinkedHashMap<>();
        for (SceneMember member : canonical.members()) {
            BodyPosition body = canonical.memberBody(state.actorLocations(), member.actorId());
            translated.put(member.actorId(), new BodyPosition(body.x() + dx, body.y() + dy, body.z() + dz));
        }
        return Map.copyOf(translated);
    }
}
