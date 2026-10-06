package io.farfrontier.palemirror.frontier.v3.model;

/** Commercial owner supplies transport consent without exporting price logic into logistics. */
final class GoodsShipmentAuthorization implements ShipmentAuthorizationPort {
    @Override public FrontierWorldStateUpdate allocationDisposed(FrontierWorldState state, Shipment shipment, ExactInventory inventory) {
        validate(state, shipment, false);
        var contract = state.companies().goodsTrade().contracts().get(shipment.authorization().claimantId());
        var receipt = new io.farfrontier.palemirror.frontier.v3.api.SubjectId("receipt:shipment-loss-"
                + java.util.UUID.nameUUIDFromBytes((shipment.id().value() + ":" + shipment.revision())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var disposition = new GoodsTradeDisposition(receipt, contract.id(), contract.revision(),
                shipment.authorization().claimId(), shipment.quantity(), GoodsTradeDisposition.Reason.OBSERVED_ALLOCATION_CHANGED);
        if (inventory.fungibleResources().claims().containsKey(shipment.authorization().claimId()))
            throw new IllegalArgumentException("cargo disposition did not release its commercial allocation");
        var economics = inventory.economics().releasePortion(contract.financialReservationId(),
                contract.deliveredUnitPrice().multiply(shipment.quantity()));
        return FrontierWorldStateUpdate.begin().inventory(inventory.withEconomics(economics))
                .companies(state.companies().withGoodsTrade(state.companies().goodsTrade().dispose(disposition)));
    }
    @Override public boolean receptionAccepted(FrontierWorldState state, Shipment shipment, ShipmentReception reception) {
        var contract = state.companies().goodsTrade().contracts().get(shipment.authorization().claimantId());
        if (contract == null) throw new IllegalArgumentException("shipment reception lost its acknowledgement owner");
        var receipt = contract.acceptances().get(reception.id());
        return receipt != null && receipt.title().claimId().equals(reception.claimId())
                && receipt.title().accountId().equals(shipment.receivingAccountId())
                && receipt.title().portions().equals(reception.lotQuantities());
    }
    @Override public java.util.Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> capacityCompletionOwner(Shipment shipment) {
        return java.util.Optional.of(shipment.authorization().claimantId());
    }
    @Override public void collect(Shipment shipment, java.util.List<FrontierDomainRelationships.Edge> edges) {
        var owner = new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.SHIPMENT, shipment.id());
        edges.add(FrontierDomainRelationships.declaredEdge(FrontierDomainRelationships.Kind.SHIPMENT_CONTRACT, owner, owner,
                new FrontierDomainRelationships.SubjectEndpoint(FrontierDomainRelationships.EntityKind.GOODS_CONTRACT,
                        shipment.authorization().claimantId()), FrontierDomainRelationships.Lifecycle.ACTIVE, "shipment:" + shipment.id().value()));
    }
    @Override public void validate(FrontierWorldState state, Shipment shipment, boolean admission) {
        ResourceClaimDelegation grant = shipment.authorization();
        GoodsTradeContract contract = state.companies().goodsTrade().contracts().get(grant.claimantId());
        ClaimAllocation claim = state.inventory().fungibleResources().claims().get(grant.claimId());
        var expected = new java.util.LinkedHashMap<>(shipment.lotQuantities());
        shipment.reception().filter(r -> r.claimId().equals(grant.claimId()) && !receptionAccepted(state, shipment, r)).ifPresent(r ->
                r.lotQuantities().forEach((lot, quantity) -> expected.merge(lot, quantity, Math::addExact)));
        if (contract == null || claim == null || claim.purpose() != ClaimPurpose.GOODS_TRADE
                || !claim.claimantId().equals(contract.id()) || !claim.economicOwnerId().equals(contract.seller().id())
                || !claim.itemKind().equals(shipment.itemKind()) || !claim.lotQuantities().equals(expected)
                || contract.outstandingClaims().getOrDefault(claim.id(), 0) != expected.values().stream().mapToInt(Integer::intValue).sum()
                || !contract.sourceContainerId().equals(shipment.sender().containerId())
                || !contract.receiverContainerId().equals(shipment.receiver().containerId())
                || (admission ? grant.authorizationRevision() != contract.revision() : grant.authorizationRevision() > contract.revision()))
            throw new IllegalArgumentException("shipment lacks the exact commercial allocation, endpoints or authorization revision");
    }
    @Override public FrontierWorldStateUpdate allocationPartitioned(FrontierWorldState state, Shipment shipment, ResourceClaimPartition partition) {
        validate(state, shipment, false);
        if (!partition.accountId().equals(shipment.carriedAccountId()) || !partition.claimId().equals(shipment.authorization().claimId()))
            throw new IllegalArgumentException("shipment partition has a foreign retained allocation");
        return FrontierWorldStateUpdate.begin().companies(state.companies().withGoodsTrade(
                state.companies().goodsTrade().partition(shipment.authorization().claimantId(), partition)));
    }
}
