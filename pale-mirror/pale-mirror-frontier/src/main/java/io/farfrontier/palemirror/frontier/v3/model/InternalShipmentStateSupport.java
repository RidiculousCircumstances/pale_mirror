package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Internal authorization lifecycle, independent of mining and independent of commercial title/payment. */
public final class InternalShipmentStateSupport {
    private InternalShipmentStateSupport() { }
    /** A replenishing source accumulates the next batch while its current transport is outstanding.
     * This is flow admission, not a lock on the receiving service point or its approach. */
    public static boolean flowAvailable(FrontierWorldState state, SubjectId sourceAccount,
            SubjectId receiver, String itemKind) {
        return state.shipments().shipments().values().stream().noneMatch(shipment -> !shipment.terminal()
                && shipment.authorization().kind() == ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT
                && shipment.sourceAccountId().equals(sourceAccount)
                && shipment.receiver().containerId().equals(receiver) && shipment.itemKind().equals(itemKind));
    }
    public static FrontierWorldState dispatch(FrontierWorldState state, SubjectId issuer, InternalShipmentDispatched event) {
        var shipment = event.shipment(); var claim = event.claim();
        if (shipment.authorization().kind() != ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT
                || !issuer.equals(shipment.authorization().claimantId()) || !claim.id().equals(shipment.authorization().claimId())
                || !claim.claimantId().equals(issuer) || !claim.economicOwnerId().equals(issuer)
                || claim.purpose() != ClaimPurpose.INTERNAL_LOGISTICS || !claim.lotQuantities().equals(shipment.lotQuantities()))
            throw new IllegalArgumentException("internal shipment lacks its exact issued resource promise");
        // Flow admission belongs to the serialized planner. Already accepted events
        // replay as facts, including batches admitted before the current policy.
        var ledger = state.inventory().fungibleResources();
        var bindings = ledger.bindings().values().stream().filter(binding -> binding.accountId().equals(shipment.sourceAccountId())).toList();
        ledger = bindings.isEmpty() ? ledger.reserve(claim, shipment.sourceAccountId())
                : ledger.reserveBound(claim, shipment.sourceAccountId(), bindings.getFirst().authorityEpoch());
        var allocated = state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(ledger)));
        return ShipmentStateSupport.dispatch(allocated, issuer, shipment);
    }
    public static FrontierWorldState receive(FrontierWorldState state, SubjectId subject, InternalShipmentReceived event) {
        var shipment = Objects.requireNonNull(state.shipments().shipments().get(event.shipmentId()), "received internal shipment");
        var receipt = shipment.reception().orElseThrow(() -> new IllegalArgumentException("internal receipt lacks actual unloading"));
        var grant = shipment.authorization(); var ledger = state.inventory().fungibleResources();
        if (shipment.cargo() instanceof ShipmentCargo.Individual cargo) {
            var item = state.inventory().items().get(cargo.itemId());
            if (!subject.equals(shipment.id()) || shipment.revision() != event.expectedRevision()
                    || grant.kind() != ResourceClaimDelegation.Kind.OWNED_GOODS_SHIPMENT
                    || !receipt.id().equals(event.receiptId()) || !receipt.claimId().equals(grant.claimId())
                    || !cargo.matches(item) || !cargo.ownerId().equals(grant.claimantId())
                    || !(item.custody() instanceof InventoryCustody.ContainerSlot slot)
                    || !slot.containerId().equals(shipment.receiver().containerId())
                    || !receipt.lotQuantities().equals(Map.of(cargo.itemId(), 1)))
                throw new IllegalArgumentException("individual owned receipt lost its exact item or destination");
            return state.withChanges(FrontierWorldStateUpdate.begin().shipments(state.shipments().replace(shipment, shipment.acknowledged(receipt.id()))));
        }
        var claim = ledger.claims().get(receipt.claimId()); var account = ledger.accounts().get(shipment.receivingAccountId());
        if (!subject.equals(shipment.id()) || shipment.revision() != event.expectedRevision()
                || (grant.kind() != ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT && grant.kind() != ResourceClaimDelegation.Kind.OWNED_GOODS_SHIPMENT) || !receipt.id().equals(event.receiptId())
                || claim == null || claim.purpose() != ClaimPurpose.INTERNAL_LOGISTICS
                || !claim.claimantId().equals(grant.claimantId()) || !claim.economicOwnerId().equals(grant.claimantId())
                || !claim.itemKind().equals(shipment.itemKind()) || !claim.lotQuantities().equals(receipt.lotQuantities())
                || account == null || !account.custody().equals(new ResourceCustody.Container(shipment.receiver().containerId()))
                || account.claimQuantities().getOrDefault(claim.id(), 0) != receipt.quantity())
            throw new IllegalArgumentException("internal stock receipt lost its retained owner, allocation or receiving custody");
        var bindings = ledger.bindings().values().stream().filter(binding -> binding.accountId().equals(account.id())).toList();
        ledger = bindings.isEmpty() ? ledger.releaseClaim(account.id(), claim.id())
                : ledger.releaseBoundClaim(account.id(), claim.id(), bindings.getFirst().authorityEpoch());
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(ledger))
                .shipments(state.shipments().replace(shipment, shipment.acknowledged(receipt.id()))));
    }
}
