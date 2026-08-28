package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable post-impact evidence for one entity selected by a real explosion. */
public record ExplosionEntityImpact(UUID entityId, String entityType, Optional<SubjectId> frontierActorId, boolean removed) {
    public ExplosionEntityImpact {
        Objects.requireNonNull(entityId, "entity id");
        if (entityType == null || entityType.isBlank() || entityType.length() > 128) throw new IllegalArgumentException("invalid explosion entity type");
        frontierActorId = Objects.requireNonNull(frontierActorId, "frontier actor id");
    }
}
