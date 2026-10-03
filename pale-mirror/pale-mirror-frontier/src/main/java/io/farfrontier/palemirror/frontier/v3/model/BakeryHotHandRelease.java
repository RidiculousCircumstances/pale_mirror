package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One atomic HOT-to-COLD baker-hand binding closure and scene-body exit. */
public record BakeryHotHandRelease(SubjectId jobId, SubjectId actorAccountId, long actorEpoch,
                                   FungiblePhysicalObservation.Stack observedHand,
                                   SceneLeaseReleased sceneRelease) implements FrontierPayload {
    public BakeryHotHandRelease {
        Objects.requireNonNull(jobId, "bakery release job");
        Objects.requireNonNull(actorAccountId, "bakery release actor account");
        Objects.requireNonNull(observedHand, "bakery release physical hand");
        Objects.requireNonNull(sceneRelease, "bakery release scene");
        if (actorEpoch < 1 || !(observedHand.address() instanceof PhysicalStackAddress.ActorHand hand)
                || hand.hand() != ActorContainerItemOrder.Hand.MAIN)
            throw new IllegalArgumentException("bakery hand release needs one current exact actor hand");
    }
    @Override public String type() { return "frontier.bakery_hot_hand_release"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
