package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact physical storage surface; Minecraft containers later represent these same slots. */
public record ContainerRecord(SubjectId id, SubjectId ownerId, int slotCount) {
    public ContainerRecord {
        Objects.requireNonNull(id, "container id"); Objects.requireNonNull(ownerId, "container owner");
        if (slotCount <= 0 || slotCount > 54) throw new IllegalArgumentException("container slot count must be 1..54");
    }
}
