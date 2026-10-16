package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Matches explicit mutual consent. It cannot invent targets, change limits or read unknown stocks. */
final class GoodsOrderMatching {
    record Endpoints(SubjectId source, SubjectId receiver) { }
    record Match(GoodsTradeContract contract, List<GoodsTradeStockAllocation> allocations) { }
    record Search(Optional<Match> match, String disposition, int checkedPairs) { }
    static List<Endpoints> endpointPairs(FrontierWorldState state, GoodsParticipant seller, GoodsParticipant buyer, long now) {
        return new io.farfrontier.palemirror.frontier.v3.model.market.MarketOfferBook(
                state.companies().goodsTrade().orders().values(), now)
                .offers(buyer.party(), Optional.of(seller.party()), Set.of()).stream()
                .filter(pair -> GoodsParticipantStorage.eligible(state, seller, pair.sell().containerId(), pair.sell().itemKind())
                        && GoodsParticipantStorage.eligible(state, buyer, pair.buy().containerId(), pair.buy().itemKind()))
                .map(pair -> new Endpoints(pair.sell().containerId(), pair.buy().containerId())).distinct()
                .limit(state.bootstrap().ruleset().goodsTrade().maximumPairReviews()).toList();
    }
    static Search first(FrontierWorldState state, GoodsParticipant reviewer, long now) {
        return first(state, reviewer, now, Optional.empty(), Optional.of(reviewer.party()));
    }
    static Search first(FrontierWorldState state, GoodsParticipant reviewer, long now,
                        Optional<GoodsTradeParty> selectedSeller, Optional<GoodsTradeParty> selectedBuyer) {
        return first(state, reviewer, now, selectedSeller, selectedBuyer, Set.of());
    }
    static Search first(FrontierWorldState state, GoodsParticipant reviewer, long now,
                        Optional<GoodsTradeParty> selectedSeller, Optional<GoodsTradeParty> selectedBuyer, Set<String> excludedCommodities) {
        return first(state, reviewer, now, selectedSeller, selectedBuyer, excludedCommodities, Optional.empty());
    }
    static Search first(FrontierWorldState state, GoodsParticipant reviewer, long now,
                        Optional<GoodsTradeParty> selectedSeller, Optional<GoodsTradeParty> selectedBuyer,
                        Set<String> excludedCommodities, Optional<Endpoints> endpoints) {
        var trade = state.companies().goodsTrade();
        var book = new io.farfrontier.palemirror.frontier.v3.model.market.MarketOfferBook(trade.orders().values(), now);
        var offers = book.buyers().stream().filter(buyer -> selectedBuyer.filter(value -> !value.equals(buyer)).isEmpty())
                .flatMap(buyer -> book.offers(buyer, selectedSeller, excludedCommodities).stream())
                .filter(pair -> endpoints.map(points -> points.source().equals(pair.sell().containerId())
                        && points.receiver().equals(pair.buy().containerId())).orElse(true))
                .filter(pair -> pair.buy().party().equals(reviewer.party()) || pair.sell().party().equals(reviewer.party())).toList();
        var cachedIntents = new HashMap<SubjectId, List<GoodsParticipantPolicy.Intent>>();
        String disposition = "NO_MUTUAL_KNOWN_QUOTE"; int checked = 0;
        for (var pair : offers) {
            var sell = pair.sell();
            var seller = trade.participants().participants().get(sell.party().id());
            if (seller == null || !seller.party().equals(sell.party())) continue;
            var buy = pair.buy();
                if (checked >= state.bootstrap().ruleset().goodsTrade().maximumPairReviews())
                    return new Search(Optional.empty(), "SEARCH_BUDGET; last=" + disposition, checked);
                checked++;
                if (sell.unitPriceLimit().compareTo(buy.unitPriceLimit()) > 0) { disposition = "PRICE_LIMIT"; continue; }
                var buyer = trade.participants().participants().get(buy.party().id());
                if (buyer == null || !buyer.party().equals(buy.party()) || !known(seller, buyer) || !known(buyer, seller)) continue;
                if (!GoodsParticipantStorage.eligible(state, seller, sell.containerId(), sell.itemKind())
                        || !GoodsParticipantStorage.eligible(state, buyer, buy.containerId(), buy.itemKind())) {
                    disposition = "STORAGE_UNAVAILABLE"; continue;
                }
                var sellerIntent = intent(state, seller, sell.itemKind(), GoodsTradeOrder.Side.SELL, cachedIntents);
                var buyerIntent = intent(state, buyer, buy.itemKind(), GoodsTradeOrder.Side.BUY, cachedIntents);
                if (sellerIntent.isEmpty() || buyerIntent.isEmpty()) { disposition = "CURRENT_RESERVE_STOCK_OR_FUNDS"; continue; }
                if (sell.unitPriceLimit().compareTo(sellerIntent.orElseThrow().limit()) < 0
                        || sell.unitPriceLimit().compareTo(buyerIntent.orElseThrow().limit()) > 0) {
                    disposition = "CURRENT_PRICE_POLICY"; continue;
                }
                // Quote matching owns price/stock/funds only. The selected logistics
                // candidate separately owns connectivity, crew and departure proof.
                int max = Math.min(Math.min(sell.availableQuantity(), buy.availableQuantity()),
                        Math.min(sellerIntent.orElseThrow().quantity(), buyerIntent.orElseThrow().quantity()));
                FixedScalar price = sell.unitPriceLimit();
                max = (int) Math.min(max, GoodsParticipantFunding.purchase(GoodsParticipantView.read(state, buyer),
                        state.bootstrap().ruleset().goodsTrade(), buy.itemKind()).raw() / price.raw());
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
                    var contract = new GoodsTradeContract(contractId, sell.id(), buy.id(), sell.party(), buy.party(), new GoodsTradeFulfillment(GoodsTradeFulfillment.Mode.SELLER_DELIVERY, sell.party()),
                            sell.containerId(), buy.containerId(), sell.itemKind(), quantity, price,
                            new SubjectId("reservation:goods/" + stem), 0, Map.of(claimId, quantity), Map.of());
                    return new Search(Optional.of(new Match(contract, List.of(new GoodsTradeStockAllocation(selected.accountId(), claim,
                            hot ? GoodsTradeStockAllocation.Authority.PHYSICAL : GoodsTradeStockAllocation.Authority.COLD, epoch)))), "MATCH", checked);
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
        return owner.known().stream().anyMatch(peer -> peer.party().equals(other.party()) && peer.settlementId().equals(other.settlementId()));
    }
    private GoodsOrderMatching() { }
}
