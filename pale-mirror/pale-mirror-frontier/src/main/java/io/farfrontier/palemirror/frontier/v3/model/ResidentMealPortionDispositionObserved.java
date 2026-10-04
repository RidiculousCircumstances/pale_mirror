package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Resource-only disposition of an exact retired portion, not a death or eating receipt. */
public record ResidentMealPortionDispositionObserved(ActorBodyId body, ActorExecutionId executionId,
        long sourceEpoch, Outcome outcome, Optional<UUID> worldCarrier) implements FrontierPayload {
    public enum Outcome { MISSING_BEFORE_LOOT, WORLD_DROP }

    public ResidentMealPortionDispositionObserved {
        Objects.requireNonNull(body);
        Objects.requireNonNull(executionId);
        Objects.requireNonNull(outcome);
        worldCarrier = Objects.requireNonNull(worldCarrier);
        if (!body.actorId().equals(executionId.actorId()) || executionId.activityKind() != ActorActivityKind.MEAL
                || sourceEpoch < 1 || worldCarrier.isPresent() != (outcome == Outcome.WORLD_DROP))
            throw new IllegalArgumentException("retired portion disposition lacks its exact body, execution or physical layout");
    }

    @Override public String type() { return "frontier.resident_meal_portion_disposition_observed"; }
}
