package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

/** Lost source food withdraws a promise, never fabricates a loaded or consumed ration. */
final class ExpeditionSupplyForfeitureOwner implements FungibleClaimForfeitureOwner {
    @Override public void validate(FrontierWorldState before, ClaimAllocation claim, FungibleResourceHandoffObserved observation) {
        var mission = before.shipments().missions().get(claim.claimantId());
        if (mission != null && mission.replenishment().filter(t -> t.claimId().equals(claim.id())).isPresent()) {
            var transfer = mission.replenishment().orElseThrow();
            if (claim.purpose() != ClaimPurpose.EXPEDITION_SUPPLY || transfer.pending().isPresent()
                    || !transfer.sourceAccountId().equals(observation.sourceAccountId()) || !transfer.lots().equals(claim.lotQuantities())
                    || !transfer.sourceEconomicOwnerId().equals(claim.economicOwnerId()) || !transfer.itemKind().equals(claim.itemKind()))
                throw new IllegalArgumentException("source change cannot bypass prepared personal replenishment");
            return;
        }
        if (claim.purpose() != ClaimPurpose.EXPEDITION_SUPPLY || mission == null || mission.stage() != TransportMission.Stage.LOADING
                || mission.supplies().isEmpty() || !claim.economicOwnerId().equals(mission.sender().settlementId()))
            throw new IllegalArgumentException("withdrawn supply claim lacks its exact loading owner");
        var a = mission.supplies().orElseThrow().allocations().stream().filter(value -> value.claimId().equals(claim.id())).findFirst().orElseThrow();
        if (a.loaded() || a.withdrawn() || a.pending().isPresent() || !a.sourceAccountId().equals(observation.sourceAccountId())
                || !a.lots().equals(claim.lotQuantities()) || !mission.supplies().orElseThrow().foodKind().equals(claim.itemKind()))
            throw new IllegalArgumentException("source food change cannot bypass a prepared provisioning effect");
    }
    @Override public FungibleForfeitureSettlement settle(FrontierWorldState before, List<ClaimAllocation> claims,
                                                        FungibleForfeitureSettlement transaction) {
        var shipments = transaction.shipments(); var inventory = transaction.inventory();
        for (var claim : claims) {
            if (transaction.inventory().fungibleResources().claims().containsKey(claim.id()))
                throw new IllegalArgumentException("supply withdrawal precedes its accounted physical release");
            var mission = shipments.missions().get(claim.claimantId());
            if (mission.replenishment().filter(t -> t.claimId().equals(claim.id())).isPresent()) {
                if (mission.replenishmentPurchase().isPresent()) inventory = GoodsSpotPurchaseAuthority.cancel(inventory, mission.replenishmentPurchase().orElseThrow());
                shipments = shipments.replaceReplenishment(mission, java.util.Optional.empty());
                continue;
            }
            var load = mission.supplies().orElseThrow();
            var allocation = load.allocations().stream().filter(a -> a.claimId().equals(claim.id())).findFirst().orElseThrow();
            shipments = shipments.replaceSupplies(mission, load.replace(allocation, allocation.sourceWithdrawn()));
        }
        return transaction.withTrade(inventory, transaction.companies()).withShipments(shipments, transaction.executions(), transaction.movements());
    }
}
