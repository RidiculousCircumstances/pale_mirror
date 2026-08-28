package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable durable claim preventing concurrent COLD and HOT execution of one route scene. */
public record SceneLease(SceneLeaseId id, SubjectId operationId, SubjectId cargoId, BlockPosition handoffPosition,
                         SimInstant handoffInstant, long revision, SceneLeaseStatus status, List<SceneMember> members) {
    public SceneLease {
        Objects.requireNonNull(id, "scene lease id");
        Objects.requireNonNull(operationId, "scene lease operation");
        Objects.requireNonNull(cargoId, "scene lease cargo");
        Objects.requireNonNull(handoffPosition, "scene lease handoff position");
        Objects.requireNonNull(handoffInstant, "scene lease handoff instant");
        if (revision < 0) throw new IllegalArgumentException("scene lease revision must be non-negative");
        Objects.requireNonNull(status, "scene lease status");
        members = List.copyOf(members);
        if (members.isEmpty() || members.size() > 32 || members.stream().map(SceneMember::actorId).distinct().count() != members.size()
                || members.stream().map(SceneMember::entityId).distinct().count() != members.size()) {
            throw new IllegalArgumentException("scene lease must have one to thirty-two distinct members and bodies");
        }
        for (SceneMember member : members) {
            if (!deterministicEntityId(id, member.actorId()).equals(member.entityId())) {
                throw new IllegalArgumentException("scene member UUID must be deterministic from lease and actor identity");
            }
        }
    }

    public static UUID deterministicEntityId(SceneLeaseId leaseId, SubjectId actorId) {
        return UUID.nameUUIDFromBytes(("frontier-v3:" + leaseId.value() + ":" + actorId.value()).getBytes(StandardCharsets.UTF_8));
    }

    public SceneLease withStatus(SceneLeaseStatus nextStatus) {
        return new SceneLease(id, operationId, cargoId, handoffPosition, handoffInstant, revision, nextStatus, members);
    }
}
