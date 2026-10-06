package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Matches explicit mutual consent. It cannot invent targets, change limits or read unknown stocks. */
final class GoodsOrderMatching {
    record Match(GoodsTradeContract contract, List<GoodsTradeStockAllocation> allocations) { }
    record Search(Optional<Match> match, String disposition, int checkedPairs) { }
    private record Pair(GoodsTradeParty seller, GoodsTradeParty buyer, String commodity) { }
    static Search first(FrontierWorldState state, GoodsParticipant reviewer, long now) {
        var trade = state.companies().goodsTrade();
        var candidates = trade.orders().values().stream().filter(order -> order.expiresAtTick() >= now
                && order.availableQuantity() > 0 && (order.party().equals(reviewer.party()) || order.counterparty().equals(reviewer.party())))
                .sorted(Comparator.comparing(GoodsTradeOrder::expiresAtTick).thenComparing(GoodsTradeOrder::id)).toList();
        var bids = new HashMap<Pair, List<GoodsTradeOrder>>();
        for (var order : candidates) if (order.side() == GoodsTradeOrder.Side.BUY)
            bids.computeIfAbsent(new Pair(order.counterparty(), order.party(), order.itemKind()), ignored -> new ArrayList<>()).add(order);
        var offers = new ArrayList<>(candidates.stream().filter(order -> order.side() == GoodsTradeOrder.Side.SELL).toList());
        if (!offers.isEmpty()) Collections.rotate(offers, -(int) (reviewer.reviewRevision() % offers.size()));
        var cachedIntents = new HashMap<SubjectId, List<GoodsParticipantPolicy.Intent>>();
        String disposition = "NO_MUTUAL_KNOWN_QUOTE"; int checked = 0;
        for (var sell : offers) {
            if (sell.side() != GoodsTradeOrder.Side.SELL) continue;
            var seller = trade.participants().participants().get(sell.party().id());
            if (seller == null || !seller.party().equals(sell.party())) continue;
            var compatibleBids = new ArrayList<>(bids.getOrDefault(new Pair(sell.party(), sell.counterparty(), sell.itemKind()), List.of()));
            if (!compatibleBids.isEmpty()) Collections.rotate(compatibleBids, -(int) (reviewer.reviewRevision() % compatibleBids.size()));
            for (var buy : compatibleBids) {
                if (checked >= state.bootstrap().ruleset().goodsTrade().maximumPairReviews())
                    return new Search(Optional.empty(), "SEARCH_BUDGET; last=" + disposition, checked);
                checked++;
                if (sell.unitPriceLimit().compareTo(buy.unitPriceLimit()) > 0) { disposition = "PRICE_LIMIT"; continue; }
                var buyer = trade.participants().participants().get(buy.party().id());
                if (buyer == null || !buyer.party().equals(buy.party()) || !known(seller, buyer) || !known(buyer, seller)) continue;
                var sellerIntent = intent(state, seller, sell.itemKind(), GoodsTradeOrder.Side.SELL, cachedIntents);
                var buyerIntent = intent(state, buyer, buy.itemKind(), GoodsTradeOrder.Side.BUY, cachedIntents);
                if (sellerIntent.isEmpty() || buyerIntent.isEmpty()) { disposition = "CURRENT_RESERVE_STOCK_OR_FUNDS"; continue; }
                if (sell.unitPriceLimit().compareTo(sellerIntent.orElseThrow().limit()) < 0
                        || sell.unitPriceLimit().compareTo(buyerIntent.orElseThrow().limit()) > 0) {
                    disposition = "CURRENT_PRICE_POLICY"; continue;
                }
                if (!GoodsShipmentPlanning.reachable(state, seller.endpoint(), buyer.endpoint(), sell.id())) { disposition = "NO_KNOWN_SAFE_ROUTE"; continue; }
                int max = Math.min(Math.min(sell.availableQuantity(), buy.availableQuantity()),
                        Math.min(sellerIntent.orElseThrow().quantity(), buyerIntent.orElseThrow().quantity()));
                FixedScalar price = sell.unitPriceLimit();
                max = (int) Math.min(max, state.inventory().economics().availableToReserve(buy.party().id()).raw() / price.raw());
                for (int quantity = Math.min(max, state.bootstrap().ruleset().goodsTrade().maximumBatch()); quantity > 0; quantity--) {
                    if (!sell.containerId().equals(buy.containerId()) && !GoodsTradeStateSupport.receivingCapacity(state,
                            buy.containerId(), buy.itemKind(), quantity)) { disposition = "RECEIVER_CAPACITY"; continue; }
                    var selection = FungibleResourceCustodySupport.selectAtContainer(state, sell.containerId(), sell.party().id(), sell.itemKind(), quantity);
                    if (selection.isEmpty() || !FungibleResourceCustodySupport.canReserve(state, selection.orElseThrow())) {
                        disposition = "SOURCE_ALLOCATION_OR_PHYSICAL_AUTHORITY"; continue;
                    }
                    var selected = selection.orElseThrow();
                    String stem = WorkOpportunityIdentity.digest(sell.id().value() + "|" + buy.id().value()
                            + "|" + sell.committedQuantity() + "|" + buy.committedQuantity());
                    SubjectId contractId = new SubjectId("contract:goods/" + stem), claimId = new SubjectId("claim:goods/" + stem);
                    var claim = new ClaimAllocation(claimId, contractId, sell.party().id(), sell.itemKind(), quantity,
                            selected.lotQuantities(), ClaimPurpose.GOODS_TRADE);
                    boolean hot = ReferenceContainerCustody.hasLiveCustody(state, sell.containerId());
                    long epoch = hot ? state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(sell.containerId())).authorityEpoch() : 0;
                    var contract = new GoodsTradeContract(contractId, sell.id(), buy.id(), sell.party(), buy.party(),
                            sell.containerId(), buy.containerId(), sell.itemKind(), quantity, price,
                            new SubjectId("reservation:goods/" + stem), 0, Map.of(claimId, quantity), Map.of());
                    return new Search(Optional.of(new Match(contract, List.of(new GoodsTradeStockAllocation(selected.accountId(), claim,
                            hot ? GoodsTradeStockAllocation.Authority.PHYSICAL : GoodsTradeStockAllocation.Authority.COLD, epoch)))), "MATCH", checked);
                }
            }
        }
        return new Search(Optional.empty(), disposition, checked);
    }
    static Optional<GoodsParticipantPolicy.Intent> intent(FrontierWorldState state, GoodsParticipant participant,
                                                         String itemKind, GoodsTradeOrder.Side side) {
        return intent(state, participant, itemKind, side, new HashMap<>());
    }
    private static Optional<GoodsParticipantPolicy.Intent> intent(FrontierWorldState state, GoodsParticipant participant,
            String itemKind, GoodsTradeOrder.Side side, Map<SubjectId, List<GoodsParticipantPolicy.Intent>> cache) {
        return cache.computeIfAbsent(participant.party().id(), ignored -> GoodsParticipantPolicies.require(participant)
                .decide(GoodsParticipantView.read(state, participant), state.bootstrap().ruleset().goodsTrade()))
                .stream().filter(value -> value.itemKind().equals(itemKind) && value.side() == side).findFirst();
    }
    private static boolean known(GoodsParticipant owner, GoodsParticipant other) {
        return owner.known().stream().anyMatch(peer -> peer.party().equals(other.party()) && peer.endpoint().equals(other.endpoint()));
    }
    private GoodsOrderMatching() { }
}
