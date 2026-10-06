package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import static io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships.*;

/** Derived current references, never a duplicate cargo/execution register. */
final class ShipmentRelationships {
    private ShipmentRelationships() { }
    static void collect(FrontierWorldState state, List<Edge> edges) {
        ShipmentStateSupport.validate(state);
        for (Shipment shipment : state.shipments().shipments().values()) {
            var owner = new SubjectEndpoint(EntityKind.SHIPMENT, shipment.id());
            Lifecycle life = shipment.terminal() ? Lifecycle.TERMINAL_RETAINED : Lifecycle.ACTIVE;
            add(edges, Kind.SHIPMENT_COURIER, owner, EntityKind.RESIDENT, shipment.execution().actorId(), life);
            add(edges, Kind.SHIPMENT_SOURCE, owner, EntityKind.CONTAINER, shipment.sender().containerId(), life);
            add(edges, Kind.SHIPMENT_RECEIVER, owner, EntityKind.CONTAINER, shipment.receiver().containerId(), life);
            if (!shipment.terminal()) {
                add(edges, Kind.SHIPMENT_CLAIM, owner, EntityKind.RESOURCE_CLAIM, shipment.authorization().claimId(), life);
                add(edges, Kind.SHIPMENT_ACCOUNT, owner, EntityKind.RESOURCE_ACCOUNT,
                        shipment.status() == Shipment.Status.AWAITING_LOAD ? shipment.sourceAccountId() : shipment.carriedAccountId(), life);
                ShipmentAuthorizationComposition.collect(shipment, edges);
            }
        }
    }
    private static void add(List<Edge> edges, Kind kind, SubjectEndpoint owner, EntityKind target, SubjectId id, Lifecycle life) {
        edges.add(declaredEdge(kind, owner, owner, new SubjectEndpoint(target, id), life, "shipment:" + owner.id().value()));
    }
}
