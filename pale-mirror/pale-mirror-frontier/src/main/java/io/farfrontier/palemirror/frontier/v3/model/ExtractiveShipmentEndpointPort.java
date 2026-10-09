package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSite;
import java.util.List;

final class ExtractiveShipmentEndpointPort implements ShipmentEndpointPort {
    @Override public ShipmentEndpoint.Kind kind() { return ShipmentEndpoint.Kind.EXTRACTIVE_SITE; }
    private ExtractionSite site(FrontierWorldState state, ShipmentEndpoint endpoint) {
        if (!(endpoint instanceof ShipmentEndpoint.ExtractiveSite declared))
            throw new IllegalArgumentException("extractive endpoint has a foreign nominal schema");
        var deposit = state.extractionSites().deposits().get(declared.siteId());
        if (deposit == null || !deposit.site().settlementId().equals(declared.settlementId())
                || !deposit.site().containerId().equals(declared.containerId())
                || !deposit.site().layout().storagePort().equals(declared.station()))
            throw new IllegalArgumentException("shipment endpoint differs from its declared extractive storage port");
        return deposit.site();
    }
    @Override public void validate(FrontierWorldState state, ShipmentEndpoint endpoint) {
        var site = site(state, endpoint); var container = state.inventory().containers().get(site.containerId());
        if (container == null || !container.ownerId().equals(site.settlementId()))
            throw new IllegalArgumentException("extractive endpoint lost its exact storage ownership");
    }
    @Override public List<KnownPedestrianRouteKnowledge.SettlementPassage> passages(FrontierWorldState state, ShipmentEndpoint endpoint) {
        validate(state, endpoint); return List.of(); // Authored outdoor access is already contributed to shared geometry.
    }
    @Override public SurfaceAnchor exteriorApproach(FrontierWorldState state, ShipmentEndpoint endpoint) {
        validate(state, endpoint); return site(state, endpoint).layout().entrance();
    }
}
