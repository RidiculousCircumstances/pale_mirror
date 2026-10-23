package io.farfrontier.palemirror.frontier.v3.model;

/** Representation adapters own stock accounting. Commercial settlement owns consent and payment. */
public final class GoodsTitleAccounting {
    /** Same physical storage requires title settlement only; no artificial shipment or slot movement. */
    public static java.util.Optional<GoodsTitleTransfer> local(FrontierWorldState state, GoodsTradeContract contract,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId claimId) {
        if (!contract.sourceContainerId().equals(contract.receiverContainerId()))
            throw new IllegalArgumentException("local goods receipt has distinct storage endpoints");
        var individual = contract.individualAllocations().get(claimId);
        if (individual != null) {
            var item = state.inventory().items().get(individual.itemId());
            if (!individual.matches(item) || !(item.custody() instanceof InventoryCustody.ContainerSlot slot)
                    || !slot.containerId().equals(contract.receiverContainerId())) return java.util.Optional.empty();
            return java.util.Optional.of(new IndividualTitleTransfer(claimId, item, contract.buyer().id()));
        }
        var account = FungibleResourceCustodySupport.accountAtContainer(state, contract.receiverContainerId()).orElse(null);
        if (account == null || !account.claimQuantities().containsKey(claimId)) return java.util.Optional.empty();
        var resources = state.inventory().fungibleResources(); var claim = resources.claims().get(claimId);
        var splits = new java.util.HashMap<io.farfrontier.palemirror.frontier.v3.api.SubjectId, io.farfrontier.palemirror.frontier.v3.api.SubjectId>();
        for (var lot : claim.lotQuantities().entrySet()) if (resources.lots().get(lot.getKey()).quantity() > lot.getValue())
            splits.put(lot.getKey(), new io.farfrontier.palemirror.frontier.v3.api.SubjectId("lot:goods-local/" + StableCorrelationIdentity.digest(
                    contract.id().value() + "|" + contract.revision() + "|" + lot.getKey().value())));
        return java.util.Optional.of(new ResourceTitleTransfer(account.id(), claimId, contract.seller().id(), contract.buyer().id(),
                claim.lotQuantities(), splits));
    }
    static ExactInventory accept(FrontierWorldState state, GoodsTradeContract contract, GoodsTitleTransfer transfer,
                                 ResourceCustody destination) {
        if (!transfer.sourceOwnerId().equals(contract.seller().id()) || !transfer.destinationOwnerId().equals(contract.buyer().id()))
            throw new IllegalArgumentException("goods title has foreign parties");
        return switch (transfer) {
            case ResourceTitleTransfer title -> {
                var resources = state.inventory().fungibleResources();
                var account = resources.accounts().get(title.accountId()); var claim = resources.claims().get(title.claimId());
                if (account == null || !account.custody().equals(destination) || claim == null
                        || claim.purpose() != ClaimPurpose.GOODS_TRADE || !claim.claimantId().equals(contract.id())
                        || !claim.itemKind().equals(contract.itemKind()))
                    throw new IllegalArgumentException("goods acceptance lacks its allocated receiving custody");
                yield state.inventory().withFungibleResources(resources.transferTitle(title));
            }
            case IndividualTitleTransfer title -> {
                var allocation = contract.individualAllocations().get(title.claimId());
                var item = state.inventory().items().get(title.before().id());
                boolean atDestination = item != null && switch (destination) {
                    case ResourceCustody.Container container -> item.custody() instanceof InventoryCustody.ContainerSlot slot
                            && slot.containerId().equals(container.containerId());
                    case ResourceCustody.Actor actor -> item.custody().equals(new InventoryCustody.Actor(actor.actorId()));
                    default -> false;
                };
                if (allocation == null || !allocation.matches(item) || !title.before().equals(item) || !atDestination)
                    throw new IllegalArgumentException("individual acceptance lacks its exact item, condition or receiving custody");
                yield state.inventory().transferItemTitle(item, title.destinationOwnerId());
            }
        };
    }
    private GoodsTitleAccounting() { }
}
