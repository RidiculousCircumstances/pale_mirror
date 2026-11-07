package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

/** Lost source food withdraws a promise, never fabricates a loaded or consumed ration. */
final class ExpeditionSupplyForfeitureOwner implements FungibleClaimForfeitureOwner {
    private static java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement> motion(
            FrontierWorldState state, ClaimAllocation claim) {
        var mission = state.shipments().missions().get(claim.claimantId());
        if (mission == null) return java.util.Optional.empty();
        var refill = mission.replenishment().filter(t -> t.claimId().equals(claim.id()));
        var actor = refill.map(UnitResourceTransfer::actorId).orElseGet(() -> mission.supplies().orElseThrow().allocations().stream()
                .filter(a -> a.claimId().equals(claim.id())).findFirst().orElseThrow().actorId());
        return java.util.Optional.ofNullable(state.actorMovements().get(actor)).filter(m -> switch (m.context()) {
            case io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.ExpeditionReplenishment context ->
                    refill.isPresent() && context.missionId().equals(mission.id());
            case io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.ExpeditionSupply context ->
                    refill.isEmpty() && context.missionId().equals(mission.id()) && context.claimId().equals(claim.id());
            default -> false;
        });
    }
    @Override public FrontierWorldState checkpoint(FrontierWorldState before, ClaimAllocation claim, long atTick) {
        return motion(before, claim).map(m -> io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.retireOwned(
                before, m, atTick, FrontierWorldStateUpdate.begin())).orElse(before);
    }
    @Override public List<SourceStockClaimLossOwner.ReleasedActivity> released(FrontierWorldState before, ClaimAllocation claim) {
        return motion(before, claim).map(m -> List.of(new SourceStockClaimLossOwner.ReleasedActivity(m.order().actorId(),
                java.util.Optional.of(io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess.progress(
                    m, Math.addExact(m.issuedAtTick(), 1)).id()), java.util.OptionalLong.empty()))).orElse(List.of());
    }
    @Override public void validate(FrontierWorldState before, ClaimAllocation claim,
                                   io.farfrontier.palemirror.frontier.v3.api.SubjectId sourceAccount) {
        var mission = before.shipments().missions().get(claim.claimantId());
        if (mission != null && mission.replenishment().filter(t -> t.claimId().equals(claim.id())).isPresent()) {
            var transfer = mission.replenishment().orElseThrow();
            if (claim.purpose() != ClaimPurpose.EXPEDITION_SUPPLY || transfer.pending().isPresent()
                    || !transfer.sourceAccountId().equals(sourceAccount) || !transfer.lots().equals(claim.lotQuantities())
                    || !transfer.sourceEconomicOwnerId().equals(claim.economicOwnerId()) || !transfer.itemKind().equals(claim.itemKind()))
                throw new IllegalArgumentException("source change cannot bypass prepared personal replenishment");
            return;
        }
        if (claim.purpose() != ClaimPurpose.EXPEDITION_SUPPLY || mission == null || !mission.preparingSupplies()
                || mission.supplies().isEmpty() || !claim.economicOwnerId().equals(mission.organization().sponsor().id()))
            throw new IllegalArgumentException("withdrawn supply claim lacks its exact loading owner");
        var a = mission.supplies().orElseThrow().allocations().stream().filter(value -> value.claimId().equals(claim.id())).findFirst().orElseThrow();
        if (a.loaded() || a.withdrawn() || a.pending().isPresent() || !a.sourceAccountId().equals(sourceAccount)
                || !a.lots().equals(claim.lotQuantities()) || !mission.supplies().orElseThrow().foodKind().equals(claim.itemKind()))
            throw new IllegalArgumentException("source food change cannot bypass a prepared provisioning effect");
    }
    @Override public FungibleForfeitureSettlement settle(FrontierWorldState before, List<ClaimAllocation> claims,
                                                        FungibleForfeitureSettlement transaction) {
        var shipments = transaction.shipments(); var inventory = transaction.inventory();
        for (var claim : claims) {
            if (motion(before, claim).isPresent()) throw new IllegalArgumentException("supply source loss requires its exact movement checkpoint and clock");
            if (transaction.inventory().fungibleResources().claims().containsKey(claim.id()))
                throw new IllegalArgumentException("supply withdrawal precedes its accounted physical release");
            var mission = shipments.missions().get(claim.claimantId());
            if (mission.replenishment().filter(t -> t.claimId().equals(claim.id())).isPresent()) {
                if (mission.replenishmentPurchase().isPresent()) inventory = io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionFundingAuthority.cancelPurchase(inventory, mission.replenishmentPurchase().orElseThrow());
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
