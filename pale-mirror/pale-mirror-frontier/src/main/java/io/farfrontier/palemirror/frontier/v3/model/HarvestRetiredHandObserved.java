package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Objects;

/** Binding of a positively applied pre-loot projection, not permission to create stock. */
public record HarvestRetiredHandObserved(ActorActuationId identity, SubjectId siteId, SubjectId accountId,
        long resourceEpoch, FungiblePhysicalObservation.Stack hand, int completedCells) implements FrontierPayload {
    public HarvestRetiredHandObserved {
        Objects.requireNonNull(identity); Objects.requireNonNull(siteId); Objects.requireNonNull(accountId); Objects.requireNonNull(hand);
        if (identity.execution().activityKind() != ActorActivityKind.FIELD_HARVEST || completedCells < 1
                || resourceEpoch < 1 || !(hand.address() instanceof PhysicalStackAddress.ActorHand address)
                || address.hand() != ActorContainerItemOrder.Hand.OFF
                || !identity.body().actorId().equals(address.actorId()) || !hand.itemKind().equals("minecraft:wheat"))
            throw new IllegalArgumentException("retired hand has a foreign execution or progress");
    }
    @Override public String type() { return "frontier.harvest_retired_hand_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
