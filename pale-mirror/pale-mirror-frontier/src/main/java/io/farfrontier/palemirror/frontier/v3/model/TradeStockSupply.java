package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Trade owns remaining title-transfer obligations. Internal hauling changes custody, not supply. */
final class TradeStockSupply implements StockSupplyPort {
    @Override public Key key() { return Key.TRADE; }
    @Override public long inputDemand(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId owner, String itemKind) { return 0; }
    @Override public List<Commitment> pending(FrontierWorldState state) {
        var result = new ArrayList<Commitment>();
        for (var contract : state.companies().goodsTrade().contracts().values()) {
            if (contract.terminal()) continue;
            result.add(new Commitment(contract.id(), contract.buyer().id(), contract.itemKind(), contract.remainingQuantity(), 0));
            result.add(new Commitment(contract.id(), contract.seller().id(), contract.itemKind(), 0, contract.remainingQuantity()));
        }
        // A bounded sales opportunity is production demand, not a sale/claim/payment.
        // Without it, producing exactly the local target can never create the first surplus quote.
        var rules = state.bootstrap().ruleset().goodsTrade();
        var participants = state.companies().goodsTrade().participants().participants();
        var intentions = new HashMap<io.farfrontier.palemirror.frontier.v3.api.SubjectId, List<GoodsParticipantPolicy.Intent>>();
        for (var seller : participants.values()) for (var commodity : rules.policies().get(seller.policy())) {
            int requested = 0;
            for (var peer : seller.known()) {
                var buyer = participants.get(peer.party().id());
                if (buyer == null || !buyer.party().equals(peer.party()) || !buyer.endpoint().equals(peer.endpoint())
                        || buyer.known().stream().noneMatch(value -> value.party().equals(seller.party()) && value.endpoint().equals(seller.endpoint()))) continue;
                var intents = intentions.computeIfAbsent(buyer.party().id(), ignored -> GoodsParticipantPolicies.require(buyer)
                        .decide(GoodsParticipantView.read(state, buyer), rules));
                int wanted = intents.stream().filter(intent -> intent.side() == GoodsTradeOrder.Side.BUY
                        && intent.itemKind().equals(commodity.itemKind())
                        && intent.limit().compareTo(commodity.minimumSellPrice()) >= 0)
                        .mapToInt(GoodsParticipantPolicy.Intent::quantity).sum();
                requested = Math.min(rules.maximumBatch(), Math.addExact(requested, wanted));
            }
            if (requested > 0) result.add(new Commitment(seller.party().id(), seller.party().id(), commodity.itemKind(), 0, requested));
        }
        return List.copyOf(result);
    }
}
