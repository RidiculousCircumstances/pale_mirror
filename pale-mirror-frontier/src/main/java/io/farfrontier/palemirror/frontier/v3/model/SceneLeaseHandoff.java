package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.List;
import java.util.Objects;

/** Durable atomic transfer of exact HOT ambient bodies into one prepared scene lease. */
public record SceneLeaseHandoff(SceneLease lease, List<SceneMemberPosition> ambientMembers) implements FrontierPayload, SceneLeaseAdmission {
    public SceneLeaseHandoff {
        Objects.requireNonNull(lease, "scene lease");
        ambientMembers = List.copyOf(ambientMembers);
        if (ambientMembers.isEmpty() || ambientMembers.size() > lease.members().size()
                || ambientMembers.stream().map(SceneMemberPosition::actorId).distinct().count() != ambientMembers.size()) {
            throw new IllegalArgumentException("scene hand-off must capture one distinct ambient scene member");
        }
        if (ambientMembers.stream().anyMatch(member -> lease.members().stream().noneMatch(scene -> scene.actorId().equals(member.actorId())))) {
            throw new IllegalArgumentException("scene hand-off capture is not a scene member");
        }
        if (!lease.ambientHandoffActorIds().equals(ambientMembers.stream().map(SceneMemberPosition::actorId).collect(java.util.stream.Collectors.toSet()))) {
            throw new IllegalArgumentException("scene hand-off lease must retain exactly its captured ambient actors");
        }
    }

    @Override public String type() { return "frontier.scene_lease_handoff"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
