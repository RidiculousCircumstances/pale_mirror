package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import java.util.Objects;

/** Positive cancellation of an unused native attempt after its exact body departed.
 * It clears preparation only: no transfer, consumption, claim release or activity retirement. */
public record ResidentMealHotEffectCancelled(ActorBodyId body, ResidentMealPhysicalStep step) implements FrontierPayload {
    public ResidentMealHotEffectCancelled { Objects.requireNonNull(body); Objects.requireNonNull(step); }
    @Override public String type() { return "frontier.resident_meal_hot_effect_cancelled"; }
}
