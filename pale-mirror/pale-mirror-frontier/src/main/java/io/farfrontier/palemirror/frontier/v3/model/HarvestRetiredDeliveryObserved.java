package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Objects;
import java.util.Optional;

/** Positive applied delivery or positively unused cancellation. Neither starts more work. */
public record HarvestRetiredDeliveryObserved(ActorActuationId identity, SubjectId siteId,
        int deliveredBefore, int quantity, Optional<ResourceSiteHarvestDeliveryObservation> applied) implements FrontierPayload {
    public HarvestRetiredDeliveryObserved {
        Objects.requireNonNull(identity); Objects.requireNonNull(siteId); applied = Objects.requireNonNull(applied);
        if (identity.execution().activityKind() != ActorActivityKind.FIELD_HARVEST
                || deliveredBefore < 0 || quantity < 1 || quantity > 64
                || applied.filter(receipt -> !receipt.jobId().equals(identity.execution().activityOwnerId())
                    || !receipt.workerId().equals(identity.body().actorId()) || !receipt.siteId().equals(siteId)
                    || receipt.harvestedQuantity() != quantity).isPresent())
            throw new IllegalArgumentException("retired delivery has a foreign field owner or quantity");
    }
    @Override public String type() { return "frontier.harvest_retired_delivery_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
