package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;

import java.util.List;
import java.util.Objects;

/** Durable capture of a HOT scene before its owned bodies are released back to COLD execution. */
public record SceneLeaseReleased(SceneLeaseId leaseId, List<SceneMemberPosition> members) implements FrontierPayload {
    public SceneLeaseReleased {
        Objects.requireNonNull(leaseId, "scene lease id");
        members = List.copyOf(members);
        if (members.size() > 32 || members.stream().map(SceneMemberPosition::actorId).distinct().count() != members.size()) {
            throw new IllegalArgumentException("scene release must capture at most thirty-two distinct surviving actors");
        }
    }
    @Override public String type() { return "frontier.scene_lease_released"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
