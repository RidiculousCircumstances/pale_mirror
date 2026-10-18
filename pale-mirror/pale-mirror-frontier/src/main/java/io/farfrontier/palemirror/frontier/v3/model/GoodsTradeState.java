package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.HashMap;
import java.util.Map;

/** Bounded commercial registry, independent of the production-service order book. */
public record GoodsTradeState(Map<SubjectId, GoodsTradeOrder> orders, Map<SubjectId, GoodsTradeContract> contracts,
                              GoodsParticipantState participants,
                              io.farfrontier.palemirror.frontier.v3.model.market.MarketPriceHistory priceHistory) {
    public GoodsTradeState(Map<SubjectId, GoodsTradeOrder> orders, Map<SubjectId, GoodsTradeContract> contracts,
                           GoodsParticipantState participants) {
        this(orders, contracts, participants, io.farfrontier.palemirror.frontier.v3.model.market.MarketPriceHistory.empty());
    }
    public GoodsTradeState(Map<SubjectId, GoodsTradeOrder> orders, Map<SubjectId, GoodsTradeContract> contracts) {
        this(orders, contracts, GoodsParticipantState.empty());
    }
    public static final int MAX_CONTRACTS = 1_024;
    public static final int MAX_ORDERS = 2_048;
    public GoodsTradeState {
        orders = Map.copyOf(orders);
        java.util.Objects.requireNonNull(participants);
        java.util.Objects.requireNonNull(priceHistory);
        contracts = Map.copyOf(contracts);
        if (orders.size() > MAX_ORDERS || orders.entrySet().stream().anyMatch(e -> !e.getKey().equals(e.getValue().id()))
                || contracts.size() > MAX_CONTRACTS || contracts.entrySet().stream().anyMatch(e -> !e.getKey().equals(e.getValue().id()))) {
            throw new IllegalArgumentException("invalid bounded goods-trade registry");
        }
        var claimIds = contracts.values().stream().flatMap(c -> c.outstandingClaims().keySet().stream()).toList();
        var receiptIds = contracts.values().stream().flatMap(c -> java.util.stream.Stream.concat(
                c.acceptances().keySet().stream(), c.dispositions().keySet().stream())).toList();
        if (claimIds.stream().distinct().count() != claimIds.size()
                || receiptIds.stream().distinct().count() != receiptIds.size()
                || contracts.values().stream().map(GoodsTradeContract::financialReservationId).distinct().count() != contracts.size()) {
            throw new IllegalArgumentException("goods contracts compete for an allocation, receipt or financial hold");
        }
    }
    public GoodsTradeState recordSale(String item, io.farfrontier.palemirror.frontier.v3.model.market.MarketPriceHistory.Sale sale) {
        return new GoodsTradeState(orders, contracts, participants, priceHistory.record(item, sale));
    }
    public static GoodsTradeState empty() { return new GoodsTradeState(Map.of(), Map.of()); }
    public GoodsTradeState place(GoodsTradeOrder order) {
        if (orders.containsKey(order.id()) || order.committedQuantity() != 0) {
            throw new IllegalArgumentException("goods order must be a fresh participant authorization");
        }
        Map<SubjectId, GoodsTradeOrder> next = new HashMap<>(orders); next.put(order.id(), order);
        return new GoodsTradeState(next, contracts, participants, priceHistory);
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
        Map<SubjectId, GoodsTradeOrder> nextOrders = new HashMap<>(orders);
        nextOrders.put(sell.id(), sell.commit(contract.quantity())); nextOrders.put(buy.id(), buy.commit(contract.quantity()));
        Map<SubjectId, GoodsTradeContract> next = new HashMap<>(contracts); next.put(contract.id(), contract);
        return new GoodsTradeState(nextOrders, next, participants, priceHistory);
    }
    public GoodsTradeState withdraw(SubjectId participant, GoodsTradeOrderWithdrawn withdrawal) {
        var order = orders.get(withdrawal.orderId());
        if (order == null || !order.party().id().equals(participant)
                || order.committedQuantity() != withdrawal.expectedCommittedQuantity() || order.availableQuantity() == 0)
            throw new IllegalArgumentException("stale or foreign unmatched-order withdrawal");
        var next = new HashMap<>(orders);
        if (order.committedQuantity() == 0) next.remove(order.id());
        else next.put(order.id(), new GoodsTradeOrder(order.id(), order.party(), order.counterparty(), order.side(),
                order.containerId(), order.itemKind(), order.committedQuantity(), order.committedQuantity(),
                order.unitPriceLimit(), order.expiresAtTick()));
        return new GoodsTradeState(next, contracts, participants, priceHistory);
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
                        contract.goodsUnitPrice(), contract.deliveryUnitPrice())));
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
        for (SubjectId id : retired.orderIds()) {
            GoodsTradeOrder order = orders.get(id);
            if (order == null || now <= order.expiresAtTick()) throw new IllegalArgumentException("goods retirement needs expired orders");
        }
        for (SubjectId id : retired.contractIds()) {
            GoodsTradeContract contract = contracts.get(id);
            if (contract == null || !contract.terminal() || !retired.orderIds().contains(contract.sellOrderId())
                    || !retired.orderIds().contains(contract.buyOrderId())) {
                throw new IllegalArgumentException("goods retirement must close the complete terminal order component");
            }
        }
        if (contracts.values().stream().anyMatch(c -> !retired.contractIds().contains(c.id())
                && (retired.orderIds().contains(c.sellOrderId()) || retired.orderIds().contains(c.buyOrderId())))) {
            throw new IllegalArgumentException("goods retirement would orphan another retained contract");
        }
        Map<SubjectId, GoodsTradeOrder> nextOrders = new HashMap<>(orders); retired.orderIds().forEach(nextOrders::remove);
        Map<SubjectId, GoodsTradeContract> next = new HashMap<>(contracts); retired.contractIds().forEach(next::remove);
        return new GoodsTradeState(nextOrders, next, participants, priceHistory);
    }
    private GoodsTradeState put(GoodsTradeContract contract) {
        Map<SubjectId, GoodsTradeContract> next = new HashMap<>(contracts); next.put(contract.id(), contract);
        return new GoodsTradeState(orders, next, participants, priceHistory);
    }
    public GoodsTradeState withParticipants(GoodsParticipantState next) { return new GoodsTradeState(orders, contracts, next, priceHistory); }
    static void validateTerms(GoodsTradeContract contract, GoodsTradeOrder sell, GoodsTradeOrder buy) {
        if (sell == null || buy == null || sell.side() != GoodsTradeOrder.Side.SELL || buy.side() != GoodsTradeOrder.Side.BUY
                || !sell.party().equals(contract.seller()) || !buy.party().equals(contract.buyer())
                || !sell.counterparty().equals(contract.buyer()) || !buy.counterparty().equals(contract.seller())
                || !sell.containerId().equals(contract.sourceContainerId()) || !buy.containerId().equals(contract.receiverContainerId())
                || !sell.itemKind().equals(contract.itemKind()) || !buy.itemKind().equals(contract.itemKind())
                || !contract.goodsUnitPrice().equals(sell.unitPriceLimit())
                || contract.deliveredUnitPrice().compareTo(buy.unitPriceLimit()) > 0) {
            throw new IllegalArgumentException("goods agreement violates its exact buyer or seller authorization");
        }
    }
}
