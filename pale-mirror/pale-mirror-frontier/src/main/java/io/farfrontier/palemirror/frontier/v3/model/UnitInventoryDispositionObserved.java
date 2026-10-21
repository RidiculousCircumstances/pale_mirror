package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import java.util.Objects;
import java.util.Optional;

/** Exact post-death disposition of unclaimed carried stock, never a meal/job completion. */
public record UnitInventoryDispositionObserved(ActorBodyId body, SubjectId accountId, long sourceEpoch,
        Outcome outcome, Optional<ResourceCustody.Ground> ground) implements FrontierPayload {
    public enum Outcome { WORLD_DROP, MISSING_BEFORE_LOOT }
    public UnitInventoryDispositionObserved {
        Objects.requireNonNull(body); Objects.requireNonNull(accountId); Objects.requireNonNull(outcome);
        ground = Objects.requireNonNull(ground);
        if (sourceEpoch < 1 || ground.isPresent() != (outcome == Outcome.WORLD_DROP))
            throw new IllegalArgumentException("inventory disposition requires an exact source fence and outcome");
    }
    @Override public String type() { return "frontier.unit_inventory_disposition_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
