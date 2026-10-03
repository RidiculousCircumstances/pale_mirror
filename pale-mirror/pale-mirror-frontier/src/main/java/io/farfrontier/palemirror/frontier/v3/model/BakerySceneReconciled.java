package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;

/** Fresh exact body/hand inspection; no item issuance or replay of a pending production effect. */
public record BakerySceneReconciled(SubjectId jobId, SceneLeaseId leaseId, long leaseRevision,
        long recoveryEpoch, BodyPosition observedBody, FungiblePhysicalObservation.Stack observedHand) implements FrontierPayload {
    public BakerySceneReconciled {
        Objects.requireNonNull(jobId); Objects.requireNonNull(leaseId);
        Objects.requireNonNull(observedBody); Objects.requireNonNull(observedHand);
        if (leaseRevision < 1 || recoveryEpoch < 1
                || !(observedHand.address() instanceof PhysicalStackAddress.ActorHand hand)
                || hand.hand() != ActorContainerItemOrder.Hand.MAIN)
            throw new IllegalArgumentException("bakery reconciliation needs exact recovery and main-hand evidence");
    }
    @Override public String type() { return "frontier.bakery_scene_reconciled"; }
}
