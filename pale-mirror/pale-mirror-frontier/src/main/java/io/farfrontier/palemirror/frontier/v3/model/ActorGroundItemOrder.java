package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import java.util.*;

/** Explicit item-entity pickup. A world carrier is not assumed to be a ground item by its UUID. */
public record ActorGroundItemOrder(SubjectId ownerId, SubjectId actorId,
        ActorContainerItemOrder.Portion.Fungible portion, UUID entityId, long sourceEpoch,
        SurfaceAnchor station, ActorItemSlot actorSlot, long goalOrdinal, long goalRevision)
        implements ActorResourceItemOrder {
    public ActorGroundItemOrder {
        Objects.requireNonNull(ownerId); Objects.requireNonNull(actorId); Objects.requireNonNull(portion);
        Objects.requireNonNull(entityId); Objects.requireNonNull(station); Objects.requireNonNull(actorSlot);
        if (sourceEpoch < 1 || goalOrdinal < 0 || goalRevision < 1
                || !(portion.sourceCustody() instanceof ResourceCustody.Ground ground) || !ground.entityId().equals(entityId)
                || !io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN.contains(station, ground.position())
                || !portion.destinationCustody().equals(new ResourceCustody.Actor(actorId))
                || actorSlot instanceof ActorItemSlot.AttachedStorage)
            throw new IllegalArgumentException("ground pickup requires an exact entity, epoch and personal resource slot");
    }
    @Override public ActorContainerItemOrder.Direction direction() { return ActorContainerItemOrder.Direction.TAKE; }
    @Override public EndpointKind endpointKind() { return EndpointKind.GROUND_ITEM; }
    @Override public ActorGroundItemOrder withPortion(ActorContainerItemOrder.Portion.Fungible replacement) {
        return new ActorGroundItemOrder(ownerId, actorId, replacement, entityId, sourceEpoch, station, actorSlot, goalOrdinal, goalRevision);
    }
    @Override public MovementOrder movementOrder() {
        return new MovementOrder(ownerId, actorId, goalOrdinal, goalRevision, List.of(station),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
    @Override public boolean admitsBinding(ResourceCustody custody, PhysicalStackAddress address) {
        if (custody.equals(portion.sourceCustody()))
            return address.equals(new PhysicalStackAddress.WorldEntity(entityId));
        return ActorResourceSlotAddress.admits(actorId, actorSlot, custody, address);
    }
    @Override public void requireStationLayout(FungibleResourceLedger ledger, CustodyAccount source,
                                               List<PhysicalStackBinding> destinationBindings) {
        requireSourceEvidence(ledger);
    }
    @Override public void requireSourceEvidence(FungibleResourceLedger ledger) {
        var bindings = ledger.bindings().values().stream().filter(binding -> binding.accountId().equals(portion.sourceAccountId())).toList();
        if (bindings.size() != 1 || bindings.getFirst().authorityEpoch() != sourceEpoch
                || !bindings.getFirst().address().equals(new PhysicalStackAddress.WorldEntity(entityId)))
            throw new IllegalArgumentException("ground pickup lost its exact current item entity witness");
    }
}
