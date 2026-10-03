package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Physical witness for a previously COLD-held batch projected into the baker's HOT hand. */
public record BakeryHotHandMaterialized(SubjectId jobId, SceneLeaseId leaseId, SubjectId actorAccountId,
                                        long actorEpoch, FungiblePhysicalObservation.Stack observedHand)
        implements FrontierPayload {
    public BakeryHotHandMaterialized {
        Objects.requireNonNull(jobId, "bakery job");
        Objects.requireNonNull(leaseId, "baker scene");
        Objects.requireNonNull(actorAccountId, "actor resource account");
        Objects.requireNonNull(observedHand, "observed baker hand");
        if (actorEpoch < 1 || !(observedHand.address() instanceof PhysicalStackAddress.ActorHand hand)
                || hand.hand() != ActorContainerItemOrder.Hand.MAIN)
            throw new IllegalArgumentException("bakery materialization requires one physical baker hand");
    }
    @Override public String type() { return "frontier.bakery_hot_hand_materialized"; }
}
