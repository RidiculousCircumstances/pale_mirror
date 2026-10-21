package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.Objects;

/** Retained outcome of a transfer or job-owned exact recipe, never new work permission. */
public record BakeryRetiredEffectObserved(ActorActuationId identity, BakeryHotEffectObserved effect,
                                          boolean applied) implements FrontierPayload {
    public BakeryRetiredEffectObserved {
        Objects.requireNonNull(identity); Objects.requireNonNull(effect);
        if (identity.execution().activityKind() != ActorActivityKind.PRODUCTION
                || !identity.execution().activityOwnerId().equals(effect.jobId())
                || effect.phase() == BakeryWorkState.Phase.DELIVERED
                || !applied && (!effect.remainingSource().isEmpty() || !effect.destination().isEmpty()))
            throw new IllegalArgumentException("retired bakery effect has foreign authority or an invalid outcome");
    }
    @Override public String type() { return "frontier.bakery_retired_effect_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
