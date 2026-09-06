package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Stable functional organ, optionally exposing one exact canonical storage surface. */
public record HiveOrgan(SubjectId id, SubjectId hiveId, SubjectId nestId, HiveOrganKind kind,
                        BlockPosition anchor, Optional<SubjectId> containerId) {
    public HiveOrgan {
        Objects.requireNonNull(id, "organ id");
        Objects.requireNonNull(hiveId, "organ hive id");
        Objects.requireNonNull(nestId, "organ nest id");
        Objects.requireNonNull(kind, "organ kind");
        Objects.requireNonNull(anchor, "organ anchor");
        containerId = Objects.requireNonNull(containerId, "organ container id");
        if (kind == HiveOrganKind.STORE != containerId.isPresent()) {
            throw new IllegalArgumentException("only a hive store organ has an exact container");
        }
    }
}
