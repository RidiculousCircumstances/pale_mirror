package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact loaded-world position observed for one leased canonical actor. */
public record SceneMemberPosition(SubjectId actorId, BlockPosition position) {
    public SceneMemberPosition {
        Objects.requireNonNull(actorId, "scene actor id");
        Objects.requireNonNull(position, "scene actor position");
    }
}
