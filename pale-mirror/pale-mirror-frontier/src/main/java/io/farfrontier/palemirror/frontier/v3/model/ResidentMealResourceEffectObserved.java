package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.List;
import java.util.Objects;

/** Resource-only witness for an original prepared effect after its activity has retired.
 * The producer observes the indexed fatal body before Vanilla loot/removal, not an empty lookup. */
public record ResidentMealResourceEffectObserved(ActorBodyId body, ActorExecutionId executionId,
        ResidentMealPhysicalStep step, Outcome outcome,
        List<FungiblePhysicalObservation.Stack> remainingSource,
        List<FungiblePhysicalObservation.Stack> destination) implements FrontierPayload {
    public enum Outcome { TAKE_UNAPPLIED, TAKE_APPLIED, CONSUMPTION_APPLIED }

    public ResidentMealResourceEffectObserved {
        Objects.requireNonNull(body, "resource witness body");
        Objects.requireNonNull(executionId, "resource witness original execution");
        Objects.requireNonNull(step, "resource witness original effect fence");
        Objects.requireNonNull(outcome, "resource effect outcome");
        remainingSource = List.copyOf(Objects.requireNonNull(remainingSource));
        destination = List.copyOf(Objects.requireNonNull(destination));
        if (!body.actorId().equals(executionId.actorId()) || !step.executionId().equals(executionId)
                || remainingSource.size() > 27 || destination.size() > 1
                || (outcome == Outcome.CONSUMPTION_APPLIED ? step.phase() != ResidentMeal.Phase.CONSUME
                        : step.phase() != ResidentMeal.Phase.TAKE)
                || outcome != Outcome.TAKE_APPLIED && !destination.isEmpty()
                || outcome == Outcome.CONSUMPTION_APPLIED && remainingSource.size() > 1)
            throw new IllegalArgumentException("retired meal effect witness has a foreign identity, phase or layout");
    }
    @Override public String type() { return "frontier.resident_meal_resource_effect_observed"; }
}
