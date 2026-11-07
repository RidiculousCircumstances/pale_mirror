package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
/** Exact native source removal witness, shared by transfer and independent owner recovery. */
final class FrontierV3GroundPickupProof {
    static FrontierV3ResourceHandoffLedger.Pickup declaration(ActorGroundItemOrder order,ActorActuationId actor,MaterialSourceSelection.Slice slice){
        if(!order.actorId().equals(actor.execution().actorId())||!order.ownerId().equals(actor.execution().activityOwnerId())
                ||!slice.address().equals(new PhysicalStackAddress.WorldEntity(order.entityId()))||slice.epoch()!=order.sourceEpoch())throw new IllegalArgumentException("foreign ground pickup participant/source");
        String witness=order.ownerId().value()+"|"+order.actorId().value()+"|"+order.goalOrdinal()+"|"+order.goalRevision()
                +"|"+order.entityId()+"|"+order.sourceEpoch()+"|"+slice.before()+"|"+slice.moved()+"|"+actor.body().physicalEpoch()+"|"+actor.execution().generation();
        return new FrontierV3ResourceHandoffLedger.Pickup(order.ownerId(),order.portion().sourceAccountId(),order.entityId(),actor.execution().activityKind(),order.goalRevision(),witness);
    }
    private FrontierV3GroundPickupProof(){}
}
