package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** One atomic commercial-to-owned-transport handoff after real pickup. */
public final class GoodsTradeCollectionSupport {
    private GoodsTradeCollectionSupport() { }
    public static FrontierWorldState accept(FrontierWorldState state, SubjectId buyer, GoodsTradeCollected event, long now) {
        var shipment = state.shipments().shipments().get(event.shipmentId());
        var receipt = event.receipt();
        if (receipt.title() instanceof IndividualTitleTransfer title) return individual(state, buyer, event, now, shipment, title);
        var title = (ResourceTitleTransfer) receipt.title();
        var contract = state.companies().goodsTrade().contracts().get(receipt.contractId());
        var ledger = state.inventory().fungibleResources();
        var account = ledger.accounts().get(title.accountId()); var claim = ledger.claims().get(title.claimId());
        var mission = shipment == null ? null : shipment.transportMissionId().map(state.shipments().missions()::get).orElse(null);
        if (contract == null || !contract.buyer().id().equals(buyer)
                || contract.fulfillment().mode() != GoodsTradeFulfillment.Mode.BUYER_COLLECTION
                || shipment == null || shipment.revision() != event.expectedShipmentRevision()
                || shipment.authorization().kind() != ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT
                || !shipment.authorization().claimantId().equals(contract.id())
                || !shipment.authorization().claimId().equals(title.claimId())
                || shipment.status() != Shipment.Status.CARRYING || shipment.pendingPhysicalStep().isPresent()
                || shipment.reception().isPresent() || !shipment.carriedAccountId().equals(title.accountId())
                || !shipment.lotQuantities().equals(title.portions())
                || mission == null || mission.stage() != TransportMission.Stage.LOADING
                || mission.organization().itinerary() != TransportOrganization.Itinerary.DESTINATION_COLLECTION
                || account == null || account.claimQuantities().getOrDefault(title.claimId(), 0) != title.quantity()
                || !(account.custody().equals(new ResourceCustody.Actor(shipment.execution().actorId()))
                    || shipment.mobileContainerId().filter(id -> account.custody().equals(new ResourceCustody.Container(id))).isPresent())
                || claim == null || claim.purpose() != ClaimPurpose.GOODS_TRADE || !claim.claimantId().equals(contract.id())
                || !title.sourceOwnerId().equals(contract.seller().id()) || !title.destinationOwnerId().equals(buyer))
            throw new IllegalArgumentException("collection acceptance lacks its exact physical pickup, buyer and mission");
        ShipmentAuthorizationComposition.validateMission(state, shipment, mission.organization());
        var trade = state.companies().goodsTrade().accept(receipt, now);
        ledger = ledger.transferTitle(title);
        var ownedLots = new LinkedHashMap<SubjectId, Integer>();
        title.portions().forEach((id, quantity) -> ownedLots.put(title.splitLotIds().getOrDefault(id, id), quantity));
        var ownedClaimId = new SubjectId("claim:collected/" + UUID.nameUUIDFromBytes(
                receipt.id().value().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var owned = new ClaimAllocation(ownedClaimId, buyer, buyer, shipment.itemKind(), title.quantity(), ownedLots, ClaimPurpose.INTERNAL_LOGISTICS);
        var bindings = ledger.bindings().values().stream().filter(binding -> binding.accountId().equals(account.id())).toList();
        ledger = bindings.isEmpty() ? ledger.reserve(owned, account.id())
                : ledger.reserveBound(owned, account.id(), bindings.getFirst().authorityEpoch());
        var economics = state.inventory().economics().settlePortion(contract.financialReservationId(),
                contract.deliveredUnitPrice().multiply(title.quantity()));
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().withFungibleResources(ledger).withEconomics(economics))
                .companies(state.companies().withGoodsTrade(trade))
                .shipments(state.shipments().purchased(shipment, new ResourceClaimDelegation(
                    ResourceClaimDelegation.Kind.OWNED_GOODS_SHIPMENT, owned.id(), buyer, shipment.id(), 1), ownedLots)));
    }
    private static FrontierWorldState individual(FrontierWorldState state, SubjectId buyer, GoodsTradeCollected event,
            long now, Shipment shipment, IndividualTitleTransfer title) {
        var receipt = event.receipt(); var contract = state.companies().goodsTrade().contracts().get(receipt.contractId());
        var mission = shipment == null ? null : shipment.transportMissionId().map(state.shipments().missions()::get).orElse(null);
        if (contract == null || !contract.buyer().id().equals(buyer) || contract.fulfillment().mode() != GoodsTradeFulfillment.Mode.BUYER_COLLECTION
                || shipment == null || shipment.revision() != event.expectedShipmentRevision()
                || !(shipment.cargo() instanceof ShipmentCargo.Individual cargo) || !cargo.matches(title.before())
                || shipment.authorization().kind() != ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT
                || !shipment.authorization().claimantId().equals(contract.id()) || !shipment.authorization().claimId().equals(title.claimId())
                || shipment.status() != Shipment.Status.CARRYING || shipment.pendingPhysicalStep().isPresent() || shipment.reception().isPresent()
                || mission == null || mission.stage() != TransportMission.Stage.LOADING
                || mission.organization().itinerary() != TransportOrganization.Itinerary.DESTINATION_COLLECTION)
            throw new IllegalArgumentException("individual collection lacks its exact picked up cargo, buyer or mission");
        ShipmentAuthorizationComposition.validateMission(state, shipment, mission.organization());
        var inventory = GoodsTitleAccounting.accept(state, contract, title, shipment.carriedCustody());
        inventory = inventory.withEconomics(inventory.economics().settlePortion(contract.financialReservationId(), contract.deliveredUnitPrice()));
        var ownedClaim = new SubjectId("claim:collected/" + UUID.nameUUIDFromBytes(receipt.id().value().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory)
                .companies(state.companies().withGoodsTrade(state.companies().goodsTrade().accept(receipt, now)))
                .shipments(state.shipments().purchasedIndividual(shipment, new ResourceClaimDelegation(
                    ResourceClaimDelegation.Kind.OWNED_GOODS_SHIPMENT, ownedClaim, buyer, shipment.id(), 1), buyer)));
    }
}
