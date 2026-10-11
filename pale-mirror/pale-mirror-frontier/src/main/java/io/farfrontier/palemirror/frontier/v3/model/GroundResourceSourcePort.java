package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;

final class GroundResourceSourcePort implements ResourceSourcePort {
    @Override public ResourceSourceEndpoint.Kind kind() { return ResourceSourceEndpoint.Kind.GROUND; }
    private ResourceSourceEndpoint.Ground declared(ResourceSourceEndpoint source) {
        if (!(source instanceof ResourceSourceEndpoint.Ground ground))
            throw new IllegalArgumentException("ground source has foreign nominal schema");
        return ground;
    }
    @Override public void validate(FrontierWorldState state, ResourceSourceEndpoint source) {
        declared(source); FrontierWorldStateSupport.settlement(state.bootstrap(), source.settlementId());
    }
    @Override public void admit(FrontierWorldState state, ResourceSourceEndpoint source) {
        validate(state, source);
        var ground = declared(source); var resources = state.inventory().fungibleResources();
        var accounts = resources.accounts().values().stream().filter(account -> account.custody().equals(ground.custody())).toList();
        if (accounts.size() != 1) throw new IllegalArgumentException("ground admission requires its one current exact custody account");
        var bindings = resources.bindings().values().stream().filter(binding -> binding.accountId().equals(accounts.getFirst().id())).toList();
        if (bindings.size() != 1 || bindings.getFirst().authorityEpoch() != ground.epoch()
                || !bindings.getFirst().address().equals(new PhysicalStackAddress.WorldEntity(ground.custody().entityId())))
            throw new IllegalArgumentException("ground admission has stale or foreign entity binding evidence");
    }
    @Override public List<ShipmentEndpoint> journeyEndpoints(ResourceSourceEndpoint source, ShipmentEndpoint receiver) {
        declared(source); return List.of(receiver);
    }
    @Override public KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, ResourceSourceEndpoint source, ShipmentEndpoint receiver) {
        declared(source);
        return KnownPedestrianRouteKnowledge.forPublicJourney(state, source.settlementId(), ShipmentEndpointComposition.passages(state, receiver));
    }
    @Override public boolean coldAvailable(FrontierWorldState state, ResourceSourceEndpoint source) {
        declared(source); return false; // Native item custody is not released by player absence.
    }
    @Override public ActorResourceItemOrder pickup(ResourceSourceEndpoint source, SubjectId owner, SubjectId actor,
            ActorContainerItemOrder.Portion.Fungible portion, ActorItemSlot slot, long ordinal, long revision) {
        var ground = declared(source);
        return new ActorGroundItemOrder(owner, actor, portion, ground.custody().entityId(), ground.epoch(), ground.station(), slot, ordinal, revision);
    }
}
