package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;

/** Post-effect witness for exactly one prepared HOT meal take or consumption. */
public record ResidentMealHotEffectObserved(SubjectId residentId, ResidentMeal.Phase phase,
                                            long ambientRevision, BodyPosition observedBody,
                                            List<FungiblePhysicalObservation.Stack> remainingSource,
                                            List<FungiblePhysicalObservation.Stack> destination, ActorExecutionId executionId) implements FrontierPayload {
    public ResidentMealHotEffectObserved {
        Objects.requireNonNull(residentId, "meal resident");
        Objects.requireNonNull(executionId, "meal operation execution identity");
        if (!residentId.equals(executionId.actorId())) throw new IllegalArgumentException("meal receipt has foreign execution actor");
        Objects.requireNonNull(phase, "meal phase");
        Objects.requireNonNull(observedBody, "meal body");
        remainingSource = List.copyOf(Objects.requireNonNull(remainingSource, "meal remaining source"));
        destination = List.copyOf(Objects.requireNonNull(destination, "meal destination"));
        if ((phase != ResidentMeal.Phase.TAKE && phase != ResidentMeal.Phase.CONSUME)
                || ambientRevision < 1 || remainingSource.size() > 27 || destination.size() > 1)
            throw new IllegalArgumentException("invalid HOT meal effect witness");
    }
    @Override public String type() { return "frontier.resident_meal_hot_effect_observed"; }
}
