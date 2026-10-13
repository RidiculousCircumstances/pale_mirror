package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Same-owner logistics permission. Neither source commodity nor source facility defines this protocol. */
final class InternalShipmentAuthorization extends SameOwnerShipmentAuthorization {
    @Override public void validate(FrontierWorldState state, Shipment shipment, boolean admission) {
        var grant = shipment.authorization();
        var claim = state.inventory().fungibleResources().claims().get(grant.claimId());
        if (grant.kind() != ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT || grant.authorizationRevision() != 1
                || shipment.transportMissionId().isPresent() || shipment.mobileContainerId().isPresent()
                || !shipment.sender().settlementId().equals(grant.claimantId())
                || !shipment.receiver().settlementId().equals(grant.claimantId())
                || claim == null || claim.purpose() != ClaimPurpose.INTERNAL_LOGISTICS
                || !claim.claimantId().equals(grant.claimantId()) || !claim.economicOwnerId().equals(grant.claimantId())
                || !claim.itemKind().equals(shipment.itemKind()) || !claim.lotQuantities().equals(shipment.lotQuantities()))
            throw new IllegalArgumentException("internal shipment lost its exact same-owner allocation or endpoints");
        if (admission && !ContainerStorageAdmission.receive(state, shipment.receiver().containerId(), shipment.itemKind(),
                shipment.quantity(), Optional.empty()))
            throw new IllegalArgumentException("internal shipment has no receiving storage capacity for its exact load");
    }
}
