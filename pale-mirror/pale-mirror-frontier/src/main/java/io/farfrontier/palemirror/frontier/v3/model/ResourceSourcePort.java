package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;

/** Source-owned physical preconditions. Shared transport owns neither container interpretation nor entity pickup rules. */
interface ResourceSourcePort {
    ResourceSourceEndpoint.Kind kind();
    void validate(FrontierWorldState state, ResourceSourceEndpoint source);
    void admit(FrontierWorldState state, ResourceSourceEndpoint source);
    List<ShipmentEndpoint> journeyEndpoints(ResourceSourceEndpoint source, ShipmentEndpoint receiver);
    default KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, ResourceSourceEndpoint source, ShipmentEndpoint receiver) {
        return ShipmentEndpointComposition.knowledge(state, journeyEndpoints(source, receiver));
    }
    boolean coldAvailable(FrontierWorldState state, ResourceSourceEndpoint source);
    ActorResourceItemOrder pickup(ResourceSourceEndpoint source, SubjectId owner, SubjectId actor,
            ActorContainerItemOrder.Portion.Fungible portion, ActorItemSlot slot, long ordinal, long revision);
}
