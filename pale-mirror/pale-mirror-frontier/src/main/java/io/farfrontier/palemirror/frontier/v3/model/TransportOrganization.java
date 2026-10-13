package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Expedition sponsor and home are independent of the carried resource's owner/source. */
public record TransportOrganization(EconomicIdentity sponsor, ShipmentEndpoint home, Itinerary itinerary) {
    public enum Itinerary { SOURCE_DELIVERY, DESTINATION_COLLECTION }
    public TransportOrganization {
        Objects.requireNonNull(sponsor); Objects.requireNonNull(home); Objects.requireNonNull(itinerary);
    }
    public void require(FrontierWorldState state) {
        sponsor.require(state.inventory().economics());
        if (sponsor.kind() != EconomicOwnerKind.SETTLEMENT_TREASURY && sponsor.kind() != EconomicOwnerKind.COMPANY)
            throw new IllegalArgumentException("unsupported expedition sponsor role");
        ShipmentEndpointComposition.validate(state, home);
        if (sponsor.kind() == EconomicOwnerKind.SETTLEMENT_TREASURY && !sponsor.id().equals(home.settlementId()))
            throw new IllegalArgumentException("expedition treasury and home have different owners");
        if (sponsor.kind() == EconomicOwnerKind.COMPANY) {
            var company = state.companies().companies().get(sponsor.id());
            if (company == null || !company.settlementId().equals(home.settlementId()))
                throw new IllegalArgumentException("expedition company lost its declared home");
        }
    }
}
