package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Typed result selected by the closed scene-behavior registry on normal release. */
public record SceneReleasePlan(SubjectId owner, SceneLeaseReleased released, SceneContinuation continuation) {
    public SceneReleasePlan {
        Objects.requireNonNull(owner, "scene release owner");
        Objects.requireNonNull(released, "scene release");
        Objects.requireNonNull(continuation, "scene continuation");
    }
}
