package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Exact idempotence key for one cross-front physical consequence. */
public record OperationFrontEffectKey(SubjectId causeId, SubjectId sourceFrontId, SubjectId targetFrontId, long authorityEpoch) {
    public OperationFrontEffectKey {
        causeId = Objects.requireNonNull(causeId, "front effect cause");
        sourceFrontId = Objects.requireNonNull(sourceFrontId, "front effect source");
        targetFrontId = Objects.requireNonNull(targetFrontId, "front effect target");
        if (authorityEpoch < 0L || sourceFrontId.equals(targetFrontId)) throw new IllegalArgumentException("cross-front effect key is invalid");
    }
}
