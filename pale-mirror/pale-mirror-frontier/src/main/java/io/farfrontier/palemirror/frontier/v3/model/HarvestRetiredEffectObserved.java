package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Objects;

/** Positive native outcome of a retained cell operation, never permission for another action. */
public record HarvestRetiredEffectObserved(ActorActuationId identity,
        ResourceSiteHarvestProgressed effect, boolean applied) implements FrontierPayload {
    public HarvestRetiredEffectObserved {
        Objects.requireNonNull(identity); Objects.requireNonNull(effect);
        if (identity.execution().activityKind() != ActorActivityKind.FIELD_HARVEST
                || !identity.execution().activityOwnerId().equals(effect.jobId())
                || effect.observedHand().isEmpty()
                || !effect.observedHand().orElseThrow().address().actorId().equals(identity.body().actorId()))
            throw new IllegalArgumentException("retired field effect has a foreign owner or missing native hand");
    }
    @Override public String type() { return "frontier.harvest_retired_effect_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
