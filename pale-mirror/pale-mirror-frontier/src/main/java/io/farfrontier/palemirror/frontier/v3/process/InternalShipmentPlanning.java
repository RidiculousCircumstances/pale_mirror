package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.*;

/** Reusable same-owner container-to-container haul. Source policy supplies endpoints and commodity, not motion. */
final class InternalShipmentPlanning {
    static Optional<InternalShipmentDispatched> prepare(FrontierWorldState state, ResidentProfile worker,
            ShipmentEndpoint source, ShipmentEndpoint destination, String itemKind, int batchLimit, long tick) {
        if (batchLimit < 1 || batchLimit > 64 || !source.settlementId().equals(destination.settlementId())
                || !source.settlementId().equals(worker.settlementId())) throw new IllegalArgumentException("invalid internal hauling request");
        if (source.containerId().equals(destination.containerId()) || ReferenceContainerCustody.blocksCanonicalUse(state, source.containerId())
                || ReferenceContainerCustody.blocksCanonicalUse(state, destination.containerId())
                || state.shipments().shipments().size() >= ShipmentState.MAX_SHIPMENTS
                || !SettlementLabourAllocation.canCommitToMission(state, worker.settlementId(), ResidentWorkKind.LOGISTICS, List.of(worker.id())))
            return Optional.empty();
        var account = FungibleResourceCustodySupport.accountAtContainer(state, source.containerId()).orElse(null);
        if (account == null) return Optional.empty();
        int quantity = Math.min(batchLimit, state.inventory().fungibleResources().unclaimedQuantity(account.id(), worker.settlementId(), itemKind));
        while (quantity > 0 && !state.canReceiveFungible(destination.containerId(), itemKind, quantity)) quantity--;
        if (quantity == 0) return Optional.empty();
        var selected = FungibleResourceCustodySupport.selectAtAccount(state.inventory().fungibleResources(), account.id(), worker.settlementId(), itemKind, quantity);
        if (selected.isEmpty()) return Optional.empty();
        String request = source.containerId().value() + "\n" + destination.containerId().value() + "\n" + worker.id().value() + "\n" + tick;
        String token = UUID.nameUUIDFromBytes(request.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        var id = new SubjectId("shipment:internal/" + token); var claimId = new SubjectId("claim:internal-haul-" + token);
        if (state.shipments().shipments().containsKey(id)) return Optional.empty();
        var lots = selected.orElseThrow().lotQuantities();
        var claim = new ClaimAllocation(claimId, worker.settlementId(), worker.settlementId(), itemKind, quantity, lots, ClaimPurpose.INTERNAL_LOGISTICS);
        var receiving = FungibleResourceCustodySupport.accountAtContainer(state, destination.containerId()).map(CustodyAccount::id)
                .orElseGet(() -> ReferenceContainerCustody.scopeId(destination.containerId()));
        var shipment = new Shipment(id, new ResourceClaimDelegation(ResourceClaimDelegation.Kind.INTERNAL_SHIPMENT, claimId,
                worker.settlementId(), id, 1), state.actorExecutions().next(worker.id(), ActorActivityKind.COURIER, id),
                source, destination, account.id(), new SubjectId("custody:internal-haul-" + token), receiving, itemKind, lots, Shipment.Status.AWAITING_LOAD, 1);
        if (!GoodsShipmentPlanning.reachable(state, source, destination, id)) return Optional.empty();
        return Optional.of(new InternalShipmentDispatched(shipment, claim));
    }
    private InternalShipmentPlanning() { }
}
