package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Typed result selected by the closed scene-behavior registry after unresolved recovery. */
public record SceneRecoveryPlan(SubjectId owner, SceneLeaseRecoveryUnresolved unresolved, SceneContinuation continuation) {
    public SceneRecoveryPlan {
        Objects.requireNonNull(owner, "scene recovery owner");
        Objects.requireNonNull(unresolved, "scene recovery evidence");
        Objects.requireNonNull(continuation, "scene continuation");
    }
}
