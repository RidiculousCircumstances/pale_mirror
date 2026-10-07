package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import java.util.Objects;

/** Physical presentation of existing free stock, not a pickup, resource issue or arrival. */
public record UnitInventoryBoundObserved(ActorBodyId body, SubjectId accountId,
                                       FungiblePhysicalObservation.Stack stack) implements FrontierPayload {
    public UnitInventoryBoundObserved {
        Objects.requireNonNull(body); Objects.requireNonNull(accountId); Objects.requireNonNull(stack);
    }
    @Override public String type() { return "frontier.unit_inventory_bound_observed"; }
}
