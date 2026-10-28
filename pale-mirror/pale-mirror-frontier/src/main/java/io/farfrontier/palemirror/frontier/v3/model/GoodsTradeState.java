package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.HashMap;
import java.util.Map;

/** Bounded commercial registry, independent of the production-service order book. */
public record GoodsTradeState(Map<SubjectId, GoodsTradeOrder> orders, Map<SubjectId, GoodsTradeContract> contracts,
                              GoodsParticipantState participants,
                              io.farfrontier.palemirror.frontier.v3.model.market.MarketPriceHistory priceHistory,
                              io.farfrontier.palemirror.frontier.v3.model.market.MarketDemandHistory demandHistory) {
    public GoodsTradeState(Map<SubjectId, GoodsTradeOrder> orders, Map<SubjectId, GoodsTradeContract> contracts,
                           GoodsParticipantState participants,
                           io.farfrontier.palemirror.frontier.v3.model.market.MarketPriceHistory priceHistory) {
        this(orders, contracts, participants, priceHistory,
                io.farfrontier.palemirror.frontier.v3.model.market.MarketDemandHistory.empty());
    }
    public GoodsTradeState(Map<SubjectId, GoodsTradeOrder> orders, Map<SubjectId, GoodsTradeContract> contracts,
                           GoodsParticipantState participants) {
        this(orders, contracts, participants, io.farfrontier.palemirror.frontier.v3.model.market.MarketPriceHistory.empty());
    }
    public GoodsTradeState(Map<SubjectId, GoodsTradeOrder> orders, Map<SubjectId, GoodsTradeContract> contracts) {
        this(orders, contracts, GoodsParticipantState.empty());
    }
    public static final int MAX_CONTRACTS = 1_024;
    public static final int MAX_ORDERS = 2_048;
    private static final ImmutableNestedMapView<SubjectId, GoodsTradeContract, SubjectId, IndividualResourceAllocation> INDIVIDUAL_ALLOCATIONS =
            new ImmutableNestedMapView<>(MAX_CONTRACTS, MAX_CONTRACTS * GoodsTradeContract.MAX_ALLOCATIONS);
    /** Exact declared live allocations only. Fungible contract changes carry no individual-item claim. */
    Map<SubjectId, IndividualResourceAllocation> individualAllocations() {
        return INDIVIDUAL_ALLOCATIONS.project(GoodsTradeState.class, contracts, GoodsTradeContract::individualAllocations);
    }
    private static final ImmutableGroupView<SubjectId, GoodsTradeContract, GoodsTradeParty> BUYERS = new ImmutableGroupView<>();
    private static final ImmutableGroupView<SubjectId, GoodsTradeContract, GoodsTradeParty> SELLERS = new ImmutableGroupView<>();
    private record OrderScope(GoodsTradeParty party, GoodsTradeOrder.Side side) { }
    private static final ImmutableGroupView<SubjectId, GoodsTradeOrder, OrderScope> PARTY_ORDERS = new ImmutableGroupView<>();
    /** Declared authorizations only; matching still owns time, price and current admission. */
    public java.util.List<GoodsTradeOrder> ordersFor(GoodsTradeParty party, GoodsTradeOrder.Side side) {
        return PARTY_ORDERS.rows(GoodsTradeState.class, orders, order -> java.util.Set.of(new OrderScope(order.party(), order.side())),
                new OrderScope(java.util.Objects.requireNonNull(party), java.util.Objects.requireNonNull(side)));
    }
    /** Read-only inverse of explicit contract parties, including retained terminal rows. */
    java.util.List<GoodsTradeContract> purchases(GoodsTradeParty party) {
        return BUYERS.rows(GoodsTradeState.class, contracts, contract -> java.util.Set.of(contract.buyer()), party);
    }
    java.util.List<GoodsTradeContract> sales(GoodsTradeParty party) {
        return SELLERS.rows(GoodsTradeState.class, contracts, contract -> java.util.Set.of(contract.seller()), party);
    }
    private static final ImmutableEntryInvariantCheck<SubjectId, GoodsTradeOrder> ORDER_IDENTITIES =
            new ImmutableEntryInvariantCheck<>(MAX_ORDERS);
    private static final ImmutableEntryInvariantCheck<SubjectId, GoodsTradeContract> CONTRACT_IDENTITIES =
            new ImmutableEntryInvariantCheck<>(MAX_CONTRACTS);
    private sealed interface ExclusiveReference permits Allocation, Receipt, Funding { }
    private record Allocation(SubjectId id) implements ExclusiveReference { }
    private record Receipt(SubjectId id) implements ExclusiveReference { }
    private record Funding(SubjectId id) implements ExclusiveReference { }
    private static final ImmutableGroupView<SubjectId, GoodsTradeContract, ExclusiveReference> CONTRACT_EXCLUSIVITY = new ImmutableGroupView<>();
    public GoodsTradeState {
        orders = ImmutableLookupMap.copyOf(orders);
        java.util.Objects.requireNonNull(participants);
        java.util.Objects.requireNonNull(priceHistory);
        java.util.Objects.requireNonNull(demandHistory);
        contracts = ImmutableLookupMap.copyOf(contracts);
        if (orders.size() > MAX_ORDERS || contracts.size() > MAX_CONTRACTS) {
            throw new IllegalArgumentException("invalid bounded goods-trade registry");
        }
        var checkedOrders = orders; var checkedContracts = contracts;
        // A participant/price/demand update changes neither table. Reuse only
        // exact immutable owner evidence; changed tables and full audits still
        // check changed order pairs and all original contract exclusivity rules.
        // Full audits still visit every order before publication.
        ORDER_IDENTITIES.validate(GoodsTradeState.class, checkedOrders, (id, order) -> {
            if (!id.equals(order.id())) throw new IllegalArgumentException("invalid bounded goods-trade registry");
        });
        CONTRACT_IDENTITIES.validate(GoodsTradeState.class, checkedContracts, (id, contract) -> {
            if (!id.equals(contract.id())) throw new IllegalArgumentException("invalid bounded goods-trade registry");
        });
        validateContractIdentities(checkedContracts);
    }
    private static void validateContractIdentities(Map<SubjectId, GoodsTradeContract> contracts) {
        if (!CONTRACT_EXCLUSIVITY.uniqueMemberships(GoodsTradeState.class, contracts, contract -> {
            var references = new java.util.HashSet<ExclusiveReference>();
            contract.outstandingClaims().keySet().forEach(id -> references.add(new Allocation(id)));
            // The contract constructor already rejects acceptance/disposition
            // overlap inside one row. This checks the same role across rows.
            contract.acceptances().keySet().forEach(id -> references.add(new Receipt(id)));
            contract.dispositions().keySet().forEach(id -> references.add(new Receipt(id)));
            references.add(new Funding(contract.financialReservationId())); return references;
        })) {
            throw new IllegalArgumentException("goods contracts compete for an allocation, receipt or financial hold");
        }
    }
    public GoodsTradeState recordSale(String item, io.farfrontier.palemirror.frontier.v3.model.market.MarketPriceHistory.Sale sale) {
        return new GoodsTradeState(orders, contracts, participants, priceHistory.record(item, sale), demandHistory);
    }
    public static GoodsTradeState empty() { return new GoodsTradeState(Map.of(), Map.of()); }
    public GoodsTradeState withDemandHistory(io.farfrontier.palemirror.frontier.v3.model.market.MarketDemandHistory next) {
        return new GoodsTradeState(orders, contracts, participants, priceHistory, next);
    }
    public GoodsTradeState place(GoodsTradeOrder order) {
        if (orders.containsKey(order.id()) || order.committedQuantity() != 0) {
            throw new IllegalArgumentException("goods order must be a fresh participant authorization");
        }
        var next = ImmutableLookupMap.withEntry(orders, order.id(), order);
        return new GoodsTradeState(next, contracts, participants, priceHistory, demandHistory);
    }
    public GoodsTradeState admit(GoodsTradeContract contract, long now) {
        if (contracts.containsKey(contract.id()) || contract.revision() != 0 || contract.terminal()) {
            throw new IllegalArgumentException("goods admission must establish one fresh contract");
        }
        GoodsTradeOrder sell = orders.get(contract.sellOrderId()), buy = orders.get(contract.buyOrderId());
        validateTerms(contract, sell, buy);
        if (now > sell.expiresAtTick() || now > buy.expiresAtTick()) {
            throw new IllegalArgumentException("goods agreement names an expired participant order");
        }
        var nextOrders = ImmutableLookupMap.replacing(orders, Map.of(sell.id(), sell.commit(contract.quantity()),
                buy.id(), buy.commit(contract.quantity())));
        var next = ImmutableLookupMap.withEntry(contracts, contract.id(), contract);
        return new GoodsTradeState(nextOrders, next, participants, priceHistory, demandHistory);
    }
    public GoodsTradeState withdraw(SubjectId participant, GoodsTradeOrderWithdrawn withdrawal) {
        var order = orders.get(withdrawal.orderId());
        if (order == null || !order.party().id().equals(participant)
                || order.committedQuantity() != withdrawal.expectedCommittedQuantity() || order.availableQuantity() == 0)
            throw new IllegalArgumentException("stale or foreign unmatched-order withdrawal");
        var next = order.committedQuantity() == 0 ? ImmutableLookupMap.withoutEntry(orders, order.id())
                : ImmutableLookupMap.withEntry(orders, order.id(), new GoodsTradeOrder(order.id(), order.party(), order.side(),
                order.containerId(), order.itemKind(), order.committedQuantity(), order.committedQuantity(),
                order.unitPriceLimit(), order.expiresAtTick(), order.retiredCommittedQuantity()));
        return new GoodsTradeState(next, contracts, participants, priceHistory, demandHistory);
    }
    public GoodsTradeState accept(GoodsTradeAcceptance receipt, long now) {
        GoodsTradeContract contract = contracts.get(receipt.contractId());
        if (contract == null) throw new IllegalArgumentException("acceptance names an unknown goods contract");
        return put(contract.accept(receipt)).recordSale(receipt, now);
    }
    private GoodsTradeState recordSale(GoodsTradeAcceptance receipt, long now) {
        var contract = contracts.get(receipt.contractId());
        if (contract == null || !receipt.equals(contract.acceptances().get(receipt.id())))
            throw new IllegalArgumentException("market history requires an actual accepted portion");
        return new GoodsTradeState(orders, contracts, participants, priceHistory.record(contract.itemKind(),
                new io.farfrontier.palemirror.frontier.v3.model.market.MarketPriceHistory.Sale(receipt.id(),
                        contract.seller().id(), contract.buyer().id(), now, receipt.title().quantity(),
                        contract.goodsUnitPrice(), contract.deliveryUnitPrice())), demandHistory);
    }
    public GoodsTradeState dispose(GoodsTradeDisposition disposition) {
        GoodsTradeContract contract = contracts.get(disposition.contractId());
        if (contract == null) throw new IllegalArgumentException("disposition names an unknown goods contract");
        return put(contract.dispose(disposition));
    }
    public GoodsTradeState partition(SubjectId contractId, ResourceClaimPartition partition) {
        GoodsTradeContract contract = contracts.get(contractId);
        if (contract == null) throw new IllegalArgumentException("partition names an unknown goods contract");
        return put(contract.partition(partition));
    }
    public GoodsTradeState retire(GoodsTradeRetired retired, long now) {
        var nextOrders = new HashMap<>(orders); var nextContracts = new HashMap<>(contracts);
        for (SubjectId id : retired.contractIds()) {
            var contract = nextContracts.remove(id);
            if (contract == null || !contract.terminal())
                throw new IllegalArgumentException("only a retained terminal contract may retire");
            for (var orderId : java.util.List.of(contract.sellOrderId(), contract.buyOrderId())) {
                var order = nextOrders.get(orderId);
                if (order == null) throw new IllegalArgumentException("terminal contract lost its exact order");
                nextOrders.put(orderId, order.retireCommitment(contract.quantity()));
            }
        }
        for (SubjectId id : retired.orderIds()) {
            var order = nextOrders.get(id);
            if (order == null || order.availableQuantity() > 0 && now <= order.expiresAtTick())
                throw new IllegalArgumentException("live unexpired public consent cannot retire");
            if (nextContracts.values().stream().anyMatch(contract -> contract.sellOrderId().equals(id) || contract.buyOrderId().equals(id)))
                throw new IllegalArgumentException("order retirement would orphan a retained contract");
            nextOrders.remove(id);
        }
        return new GoodsTradeState(nextOrders, nextContracts, participants, priceHistory, demandHistory);
    }
    private GoodsTradeState put(GoodsTradeContract contract) {
        var next = ImmutableLookupMap.withEntry(contracts, contract.id(), contract);
        return new GoodsTradeState(orders, next, participants, priceHistory, demandHistory);
    }
    public GoodsTradeState withParticipants(GoodsParticipantState next) { return new GoodsTradeState(orders, contracts, next, priceHistory, demandHistory); }
    static void validateTerms(GoodsTradeContract contract, GoodsTradeOrder sell, GoodsTradeOrder buy) {
        if (sell == null || buy == null || sell.side() != GoodsTradeOrder.Side.SELL || buy.side() != GoodsTradeOrder.Side.BUY
                || !sell.party().equals(contract.seller()) || !buy.party().equals(contract.buyer())
                || !sell.containerId().equals(contract.sourceContainerId()) || !buy.containerId().equals(contract.receiverContainerId())
                || !sell.itemKind().equals(contract.itemKind()) || !buy.itemKind().equals(contract.itemKind())
                || !contract.goodsUnitPrice().equals(sell.unitPriceLimit())
                || contract.deliveredUnitPrice().compareTo(buy.unitPriceLimit()) > 0) {
            throw new IllegalArgumentException("goods agreement violates its exact buyer or seller authorization");
        }
    }
}
