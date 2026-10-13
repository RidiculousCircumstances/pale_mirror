package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;

final class ContainerResourceSourcePort implements ResourceSourcePort {
    @Override public ResourceSourceEndpoint.Kind kind() { return ResourceSourceEndpoint.Kind.CONTAINER; }
    @Override public void validate(FrontierWorldState state, ResourceSourceEndpoint source) {
        ShipmentEndpointComposition.validate(state, ResourceSourceComposition.requireContainer(source));
    }
    @Override public List<ShipmentEndpoint> journeyEndpoints(ResourceSourceEndpoint source, ShipmentEndpoint receiver) {
        return List.of(ResourceSourceComposition.requireContainer(source), receiver);
    }
    @Override public void admit(FrontierWorldState state, ResourceSourceEndpoint source) {
        validate(state, source);
        if (source instanceof ShipmentEndpoint.ProductionPort)
            throw new IllegalArgumentException("declared machine receiving port cannot serve as a haul source");
    }
    @Override public boolean coldAvailable(FrontierWorldState state, ResourceSourceEndpoint source) {
        var id = ResourceSourceComposition.requireContainer(source).containerId();
        return !ReferenceContainerCustody.hasLiveCustody(state, id) && !ReferenceContainerCustody.blocksCanonicalUse(state, id);
    }
    @Override public ActorResourceItemOrder pickup(ResourceSourceEndpoint source, SubjectId owner, SubjectId actor,
            ActorContainerItemOrder.Portion.Fungible portion, ActorItemSlot slot, long ordinal, long revision) {
        var endpoint = ResourceSourceComposition.requireContainer(source);
        return new ActorContainerItemOrder(owner, actor, ActorContainerItemOrder.Direction.TAKE, portion,
                new ActorContainerItemOrder.ContainerEndpoint.FungibleContainer(endpoint.containerId()), endpoint.station(), slot, ordinal, revision);
    }
}
