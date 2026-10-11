package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import java.util.List;

/** Source-neutral custody instruction. A process retains the order; the ledger owns conservation. */
public sealed interface ActorResourceItemOrder permits ActorContainerItemOrder, ActorGroundItemOrder {
    enum EndpointKind { CONTAINER, GROUND_ITEM }
    EndpointKind endpointKind();
    SubjectId ownerId();
    SubjectId actorId();
    ActorContainerItemOrder.Direction direction();
    ActorContainerItemOrder.Portion portion();
    SurfaceAnchor station();
    ActorItemSlot actorSlot();
    long goalOrdinal();
    long goalRevision();
    MovementOrder movementOrder();
    ActorResourceItemOrder withPortion(ActorContainerItemOrder.Portion.Fungible replacement);
    void requireSourceEvidence(FungibleResourceLedger ledger);

    /** Endpoint adapters validate only their declared physical surface, never the owner's job. */
    boolean admitsBinding(ResourceCustody custody, PhysicalStackAddress address);
    void requireStationLayout(FungibleResourceLedger ledger, CustodyAccount source,
                              List<PhysicalStackBinding> destinationBindings);
}
