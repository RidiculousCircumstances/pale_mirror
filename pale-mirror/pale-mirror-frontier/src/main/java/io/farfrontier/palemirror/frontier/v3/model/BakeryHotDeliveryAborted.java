package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import java.util.Optional;

/** A destination changed before delivery: the exact complete source is still in the same hand. */
public record BakeryHotDeliveryAborted(SubjectId jobId, SceneLeaseId leaseId, int destinationSlot,
                                       long sourceEpoch, FungiblePhysicalObservation.Stack unchangedHand,
                                       Optional<SubjectId> exactItemId, BakeryWorkBlock occupiedDestination)
        implements FrontierPayload {
    public BakeryHotDeliveryAborted {
        Objects.requireNonNull(jobId); Objects.requireNonNull(leaseId); Objects.requireNonNull(unchangedHand);
        exactItemId = Objects.requireNonNull(exactItemId); Objects.requireNonNull(occupiedDestination);
        if (destinationSlot < 0 || destinationSlot >= 27 || sourceEpoch < 0
                || !(unchangedHand.address() instanceof PhysicalStackAddress.ActorHand)
                || occupiedDestination.reason() != BakeryWorkBlock.Reason.DESTINATION_OCCUPIED
                || occupiedDestination.slot() != destinationSlot || occupiedDestination.observedCount() <= 0)
            throw new IllegalArgumentException("delivery cancellation lacks an unchanged hand and occupied destination");
    }
    @Override public String type() { return "frontier.bakery_hot_delivery_aborted"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
