package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Map;

/** Commercial owner composes stock, money and consent before one canonical update. */
public final class GoodsTradeStateSupport {
    private GoodsTradeStateSupport() { }
    public static boolean receivingCapacity(FrontierWorldState state, SubjectId container, String commodity, int quantity) {
        return ContainerStorageAdmission.receive(state, container, commodity, quantity, java.util.Optional.empty());
    }

    public static FrontierWorldState place(FrontierWorldState state, SubjectId subject, GoodsTradeOrder order, long now) {
        if (!subject.equals(order.party().id()) || order.expiresAtTick() < now) {
            throw new IllegalArgumentException("only the current participant can authorize its goods order");
        }
        order.party().validate(state.inventory().economics()); order.counterparty().validate(state.inventory().economics());
        requireContainer(state, order.containerId());
        return state.withCompanies(state.companies().withGoodsTrade(state.companies().goodsTrade().place(order)));
    }

    public static FrontierWorldState reserve(FrontierWorldState state, SubjectId subject, GoodsTradeContract contract,
                                             List<GoodsTradeStockAllocation> allocations, long now) {
        if (!subject.equals(contract.seller().id()) || allocations.isEmpty() || allocations.size() > 64) {
            throw new IllegalArgumentException("goods reservation lacks its declared seller or stock allocations");
        }
        contract.seller().validate(state.inventory().economics()); contract.buyer().validate(state.inventory().economics());
        requireContainer(state, contract.sourceContainerId()); requireContainer(state, contract.receiverContainerId());
        if (!contract.sourceContainerId().equals(contract.receiverContainerId())
                && !ContainerStorageAdmission.receive(state, contract.receiverContainerId(), contract.itemKind(),
                        contract.quantity(), java.util.Optional.empty())) {
            throw new IllegalArgumentException("goods receiver has insufficient unreserved storage capacity");
        }
        GoodsTradeState trade = state.companies().goodsTrade().admit(contract, now);
        var declared = allocations.stream().collect(java.util.stream.Collectors.toMap(a -> a.claim().id(), a -> a.claim().quantity()));
        if (!declared.equals(contract.outstandingClaims())) {
            throw new IllegalArgumentException("goods reservation omits or duplicates exact contract allocations");
        }
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        for (GoodsTradeStockAllocation allocation : allocations) {
            ClaimAllocation claim = allocation.claim(); CustodyAccount account = resources.accounts().get(allocation.accountId());
            if (account == null || !(account.custody() instanceof ResourceCustody.Container container)
                    || !container.containerId().equals(contract.sourceContainerId())
                    || !claim.claimantId().equals(contract.id()) || !claim.economicOwnerId().equals(contract.seller().id())
                    || !claim.itemKind().equals(contract.itemKind())) {
                throw new IllegalArgumentException("goods reservation has a foreign resource owner or source");
            }
            resources = switch (allocation.authority()) {
                case COLD -> resources.reserve(claim, account.id());
                case PHYSICAL -> resources.reserveBound(claim, account.id(), allocation.epoch());
            };
        }
        EconomicLedger economics = state.inventory().economics().reserve(new FinancialReservation(
                contract.financialReservationId(), contract.buyer().id(), contract.seller().id(), contract.id(), contract.outstandingPrice()));
        ExactInventory inventory = state.inventory().withFungibleResources(resources).withEconomics(economics);
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory)
                .companies(state.companies().withGoodsTrade(trade)));
    }

    public static FrontierWorldState accept(FrontierWorldState state, SubjectId subject, GoodsTradeAcceptance receipt) {
        GoodsTradeContract contract = state.companies().goodsTrade().contracts().get(receipt.contractId());
        if (contract == null || !subject.equals(contract.buyer().id())) {
            throw new IllegalArgumentException("goods acceptance lacks its exact receiving buyer");
        }
        GoodsTradeState trade = state.companies().goodsTrade().accept(receipt);
        ResourceTitleTransfer title = receipt.title();
        CustodyAccount account = state.inventory().fungibleResources().accounts().get(title.accountId());
        ClaimAllocation claim = state.inventory().fungibleResources().claims().get(title.claimId());
        if (account == null || !(account.custody() instanceof ResourceCustody.Container container)
                || !container.containerId().equals(contract.receiverContainerId()) || claim == null
                || claim.purpose() != ClaimPurpose.GOODS_TRADE || !claim.claimantId().equals(contract.id())
                || !claim.itemKind().equals(contract.itemKind())
                || !title.sourceOwnerId().equals(contract.seller().id()) || !title.destinationOwnerId().equals(contract.buyer().id())) {
            throw new IllegalArgumentException("goods acceptance has no exact already-accounted receiving custody");
        }
        FungibleResourceLedger resources = state.inventory().fungibleResources().transferTitle(title);
        EconomicLedger economics = state.inventory().economics().settlePortion(contract.financialReservationId(),
                contract.deliveredUnitPrice().multiply(title.quantity()));
        ExactInventory inventory = state.inventory().withFungibleResources(resources).withEconomics(economics);
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory)
                .companies(state.companies().withGoodsTrade(trade)));
    }

    /** Full recovery and publication audit; receipts are historical, never live lot/account claims. */
    public static void validate(FrontierWorldState state) {
        GoodsTradeState trade = state.companies().goodsTrade();
        for (var participant : trade.participants().participants().values()) {
            participant.policy().validate(participant.party());
            var account = state.inventory().economics().require(participant.party().id());
            if (account.ownerKind() != participant.party().kind()) throw new IllegalArgumentException("participant lost its nominal economic owner");
            ShipmentEndpointComposition.validate(state, participant.endpoint());
            if (participant.policy() == GoodsPolicyKind.PUBLIC_SETTLEMENT
                    && !participant.party().id().equals(participant.endpoint().settlementId()))
                throw new IllegalArgumentException("public goods policy declared another settlement endpoint");
            if (participant.policy() == GoodsPolicyKind.OWN_ACCOUNT_COMPANY) {
                var company = state.companies().companies().get(participant.party().id());
                if (company == null || !company.settlementId().equals(participant.endpoint().settlementId()))
                    throw new IllegalArgumentException("company policy lost its exact legal home");
            }
            for (var peer : participant.known()) {
                if (state.inventory().economics().require(peer.party().id()).ownerKind() != peer.party().kind())
                    throw new IllegalArgumentException("known goods peer has forged nominal economic identity");
                ShipmentEndpointComposition.validate(state, peer.endpoint());
            }
        }
        Map<SubjectId, Integer> orderTotals = new java.util.HashMap<>();
        for (GoodsTradeOrder order : trade.orders().values()) {
            if (state.inventory().economics().require(order.party().id()).ownerKind() != order.party().kind()
                    || state.inventory().economics().require(order.counterparty().id()).ownerKind() != order.counterparty().kind()) {
                throw new IllegalArgumentException("goods order " + order.id().value() + " has a forged participant kind");
            }
            requireContainer(state, order.containerId());
        }
        for (GoodsTradeContract contract : trade.contracts().values()) {
            GoodsTradeState.validateTerms(contract, trade.orders().get(contract.sellOrderId()), trade.orders().get(contract.buyOrderId()));
            orderTotals.merge(contract.sellOrderId(), contract.quantity(), Math::addExact);
            orderTotals.merge(contract.buyOrderId(), contract.quantity(), Math::addExact);
            requireContainer(state, contract.sourceContainerId()); requireContainer(state, contract.receiverContainerId());
            FinancialReservation hold = state.inventory().economics().reservations().get(contract.financialReservationId());
            if (contract.terminal() ? hold != null : hold == null || !hold.reasonId().equals(contract.id())
                    || !hold.payerId().equals(contract.buyer().id()) || !hold.payeeId().equals(contract.seller().id())
                    || !hold.amount().equals(contract.outstandingPrice())) {
                throw new IllegalArgumentException("goods contract has a missing or mismatched financial hold");
            }
            for (var allocation : contract.outstandingClaims().entrySet()) {
                ClaimAllocation claim = state.inventory().fungibleResources().claims().get(allocation.getKey());
                if (claim == null || claim.purpose() != ClaimPurpose.GOODS_TRADE || !claim.claimantId().equals(contract.id())
                        || !claim.economicOwnerId().equals(contract.seller().id()) || !claim.itemKind().equals(contract.itemKind())
                        || claim.quantity() != allocation.getValue()) {
                    throw new IllegalArgumentException("goods contract has a missing or mismatched resource allocation");
                }
            }
        }
        for (GoodsTradeOrder order : trade.orders().values()) if (order.committedQuantity() != orderTotals.getOrDefault(order.id(), 0)) {
            throw new IllegalArgumentException("goods order commitment is not accounted by retained contracts");
        }
        for (ClaimAllocation claim : state.inventory().fungibleResources().claims().values()) {
            if (claim.purpose() != ClaimPurpose.GOODS_TRADE) continue;
            GoodsTradeContract contract = trade.contracts().get(claim.claimantId());
            if (contract == null || !contract.outstandingClaims().containsKey(claim.id())) {
                throw new IllegalArgumentException("goods allocation lacks its exact commercial owner");
            }
        }
    }

    static void validateTransition(FrontierWorldState before, FrontierWorldState after) {
        if (before.companies().goodsTrade() == after.companies().goodsTrade()) return;
        for (GoodsTradeContract contract : before.companies().goodsTrade().contracts().values()) {
            if (!contract.terminal() && !after.companies().goodsTrade().contracts().containsKey(contract.id())) {
                throw new IllegalArgumentException("live commercial obligation cannot be erased: " + contract.id().value());
            }
        }
    }

    public static FrontierWorldState retire(FrontierWorldState state, SubjectId subject, GoodsTradeRetired retired, long now) {
        if (!subject.equals(GoodsTradeMarketIdentity.OWNER) || state.physicalIntents().values().stream()
                .anyMatch(i -> retired.contractIds().contains(i.causeSubjectId()))) {
            throw new IllegalArgumentException("goods retirement lacks the process owner or retains a physical effect");
        }
        var trade = state.companies().goodsTrade().retire(retired, now);
        if (state.shipments().shipments().values().stream().anyMatch(s -> (!s.terminal() || s.reception().isPresent())
                && retired.contractIds().contains(s.authorization().claimantId())))
            throw new IllegalArgumentException("commercial retirement retains a live shipment");
        return state.withCompanies(state.companies().withGoodsTrade(trade));
    }

    /** Source-side preparation for bounded shipments; no movement, payment or title change. */
    public static FrontierWorldState partition(FrontierWorldState state, SubjectId subject, SubjectId contractId,
                                               ResourceClaimPartition partition) {
        GoodsTradeContract contract = state.companies().goodsTrade().contracts().get(contractId);
        var resources = state.inventory().fungibleResources();
        CustodyAccount account = resources.accounts().get(partition.accountId());
        if (state.shipments().holds(partition.claimId()))
            throw new IllegalArgumentException("dispatched allocation cannot change beneath its shipment");
        if (contract == null || !subject.equals(contract.seller().id()) || account == null
                || !(account.custody() instanceof ResourceCustody.Container container)
                || !container.containerId().equals(contract.sourceContainerId())
                || state.physicalIntents().values().stream().anyMatch(i -> i.causeSubjectId().equals(contract.id()))) {
            throw new IllegalArgumentException("commercial partition lacks a source-side safe preparation boundary");
        }
        GoodsTradeState trade = state.companies().goodsTrade().partition(contractId, partition);
        resources = resources.partitionClaim(partition);
        return state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory().withFungibleResources(resources))
                .companies(state.companies().withGoodsTrade(trade)));
    }

    /** Only the seller can abandon an unbound allocation still at the declared source. */
    public static FrontierWorldState cancelBeforeLoading(FrontierWorldState state, SubjectId subject,
                                                         GoodsTradeDisposition disposition) {
        GoodsTradeContract contract = state.companies().goodsTrade().contracts().get(disposition.contractId());
        if (contract == null || !subject.equals(contract.seller().id())
                || disposition.reason() != GoodsTradeDisposition.Reason.CANCELLED_BEFORE_LOADING) {
            throw new IllegalArgumentException("goods cancellation lacks its declared source owner");
        }
        GoodsTradeState trade = state.companies().goodsTrade().dispose(disposition);
        if (state.shipments().holds(disposition.claimId()))
            throw new IllegalArgumentException("cancellation must settle its dispatched shipment first");
        var resources = state.inventory().fungibleResources();
        CustodyAccount account = resources.accounts().values().stream()
                .filter(a -> a.claimQuantities().containsKey(disposition.claimId())).findFirst().orElseThrow(
                        () -> new IllegalArgumentException("goods cancellation lost its exact allocation"));
        if (!(account.custody() instanceof ResourceCustody.Container container)
                || !container.containerId().equals(contract.sourceContainerId())
                || state.physicalIntents().values().stream().anyMatch(i -> i.causeSubjectId().equals(contract.id()))) {
            throw new IllegalArgumentException("goods cancellation cannot erase a transport or physical obligation");
        }
        resources = resources.releaseClaim(account.id(), disposition.claimId());
        var economics = state.inventory().economics().releasePortion(contract.financialReservationId(),
                contract.deliveredUnitPrice().multiply(disposition.quantity()));
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().withFungibleResources(resources).withEconomics(economics))
                .companies(state.companies().withGoodsTrade(trade)));
    }

    private static void requireContainer(FrontierWorldState state, SubjectId id) {
        if (!state.inventory().containers().containsKey(id)) throw new IllegalArgumentException("goods endpoint has no declared container");
    }
}
