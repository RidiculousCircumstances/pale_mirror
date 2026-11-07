package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import io.farfrontier.palemirror.frontier.v3.model.geometry.BlockInteractionReach;

/** Registered ordinary storage geometry; the logistics coordinator does not interpret buildings. */
final class StorageShipmentEndpointPort implements ShipmentEndpointPort {
    private static final ImmutableKeyedInputView<ShipmentEndpoint, SurfaceAnchor> CONTACTS = new ImmutableKeyedInputView<>(512);
    @Override public ShipmentEndpoint.Kind kind() { return ShipmentEndpoint.Kind.DECLARED_STORAGE; }
    @Override public ShipmentReceiverReplacement replacementPolicy() { return StorageShipmentReceiverReplacement.INSTANCE; }
    @Override public java.util.Optional<SettlementStructure> supportingFacility(FrontierWorldState state, ShipmentEndpoint endpoint) {
        var storage = declaration(state, endpoint);
        return storage.facilityId().map(id -> facility(state, storage));
    }
    private ShipmentEndpoint.Storage declaration(FrontierWorldState state, ShipmentEndpoint endpoint) {
        if (!(endpoint instanceof ShipmentEndpoint.Storage storage))
            throw new IllegalArgumentException("storage endpoint has a foreign nominal schema");
        var container = state.inventory().containers().get(storage.containerId());
        var surface = state.inventory().surfaces().get(storage.containerId());
        if (container == null || surface == null || !container.ownerId().equals(storage.settlementId())
                || container.storage().isEmpty() || !container.storage().orElseThrow().endpoint().withStation(storage.station()).equals(storage)
                || !surface.fixed() || !BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN.contains(storage.station(), surface.position()))
            throw new IllegalArgumentException("storage endpoint lost its exact declared owner, policy or reachable surface");
        return storage;
    }
    @Override public List<ImmutableReferenceIndex.Read<FrontierWorldState>> validationReads(ShipmentEndpoint endpoint) {
        return List.of(ShipmentEndpointReferences.CONTAINERS.at(endpoint.containerId()),
                ShipmentEndpointReferences.SURFACES.at(endpoint.containerId()));
    }
    @Override public void validate(FrontierWorldState state, ShipmentEndpoint endpoint) {
        var storage = declaration(state, endpoint);
        if (storage.facilityId().isPresent()) facility(state, storage);
    }
    private SettlementStructure facility(FrontierWorldState state, ShipmentEndpoint.Storage storage) {
        return FrontierWorldStateSupport.settlement(state.bootstrap(), storage.settlementId()).structures().stream()
                .filter(value -> storage.facilityId().filter(value.id()::equals).isPresent()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("storage access lost its declared facility"));
    }
    @Override public List<KnownPedestrianRouteKnowledge.SettlementPassage> passages(FrontierWorldState state, ShipmentEndpoint endpoint) {
        var storage = declaration(state, endpoint);
        // A registered in-building container grants its declared interaction passage,
        // including threshold/interior floors. This is walking knowledge, not a machine,
        // resource or service permit; witnessed obstructions remain hard barriers.
        return storage.facilityId().isEmpty() ? List.of() : List.of(new KnownPedestrianRouteKnowledge.SettlementPassage(
                storage.settlementId(), new KnownPedestrianRouteKnowledge.Passage(facility(state, storage),
                    KnownPedestrianRouteKnowledge.Passage.Reach.STATIONS)));
    }
    @Override public SurfaceAnchor exteriorApproach(FrontierWorldState state, ShipmentEndpoint endpoint) {
        validate(state, endpoint); return ((ShipmentEndpoint.Storage) endpoint).exteriorApproach();
    }
    @Override public List<SurfaceAnchor> stations(FrontierWorldState state,ShipmentEndpoint endpoint) {
        validate(state,endpoint); return List.of(endpoint.station());
    }
    @Override public List<SurfaceAnchor> approachStations(FrontierWorldState state,ShipmentEndpoint endpoint,KnownPedestrianRouteKnowledge actorKnowledge) {
        validate(state,endpoint);
        var position=state.inventory().surfaces().get(endpoint.containerId()).position();
        return actorKnowledge.interactionStations(position,BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN);
    }
    @Override public List<SurfaceAnchor> declaredStations(FrontierBootstrap bootstrap, ShipmentEndpoint endpoint) {
        if (!(endpoint instanceof ShipmentEndpoint.Storage))
            throw new IllegalArgumentException("storage endpoint has a foreign nominal schema");
        return List.of(endpoint.station());
    }
    @Override public SurfaceAnchor interactionStation(FrontierWorldState state, ShipmentEndpoint endpoint) {
        validate(state, endpoint);
        var knowledge = ShipmentEndpointComposition.knowledge(state, List.of(endpoint))
                .forProfile(io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile.VILLAGER);
        if (knowledge.clearStation(endpoint.station())) return endpoint.station();
        // Old authored records can prefer the top of a lower barrel. Resolve an
        // actual connected contact for every consumer, including meals, without
        // changing that container's identity, contents or retained physical effects.
        return CONTACTS.getPublished(state.bootstrap(), endpoint, List.of(knowledge.geometry().version()), () -> {
            var candidates = approachStations(state, endpoint, knowledge);
            var exterior = ((ShipmentEndpoint.Storage) endpoint).exteriorApproach();
            var targets = java.util.Set.copyOf(candidates);
            return io.farfrontier.palemirror.frontier.v3.model.geometry.ReachableSurfaceArea.select(
                knowledge.geometry(), List.of(exterior), exterior, 16, 1, 4096, targets::contains).selected().stream().findFirst()
                // No positive geometry proof: keep an unresolved hint. Navigation
                // still rejects it; this does not declare the old contact usable.
                .orElse(endpoint.station());
        });
    }
}
