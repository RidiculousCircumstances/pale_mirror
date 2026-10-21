package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;

/** Exact field owner retirement after native effects and any carried yield have settled. */
public record HarvestDeathSettled(SubjectId siteId, ActorActuationId identity, int completedCells,
        int harvestedQuantity, int deliveredQuantity, long sourceEpoch, Optional<ResourceCustody.Ground> ground)
        implements FrontierPayload {
    public HarvestDeathSettled {
        Objects.requireNonNull(siteId); Objects.requireNonNull(identity); ground = Objects.requireNonNull(ground);
        if (identity.execution().activityKind() != ActorActivityKind.FIELD_HARVEST || completedCells < 0
                || harvestedQuantity < deliveredQuantity || deliveredQuantity < 0 || sourceEpoch < 0
                || ground.isPresent() != (harvestedQuantity > deliveredQuantity)
                || ground.isEmpty() != (sourceEpoch == 0))
            throw new IllegalArgumentException("invalid field death cargo disposition");
    }
    @Override public String type() { return "frontier.harvest_death_settled"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
