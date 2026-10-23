package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Matches explicit mutual consent. It cannot invent targets, change limits or read unknown stocks. */
final class GoodsOrderMatching {
    private static final ImmutableInputView<Map<Long, io.farfrontier.palemirror.frontier.v3.model.market.MarketOfferBook>> BOOKS = new ImmutableInputView<>();
    private static synchronized io.farfrontier.palemirror.frontier.v3.model.market.MarketOfferBook book(FrontierWorldState state, long now) {
        var orders = state.companies().goodsTrade().orders();
        var times = BOOKS.get(List.of(orders), HashMap::new);
        if (times.size() >= 8 && !times.containsKey(now)) times.clear();
        return times.computeIfAbsent(now, ignored -> new io.farfrontier.palemirror.frontier.v3.model.market.MarketOfferBook(orders.values(), now));
    }
    record Endpoints(SubjectId source, SubjectId receiver) { }
    record Match(GoodsTradeContract contract, List<GoodsTradeStockAllocation> allocations) { }
    record Search(Optional<Match> match, String disposition, int checkedPairs) { }
    static List<io.farfrontier.palemirror.frontier.v3.model.market.MarketOfferBook.Pair> quotes(
            FrontierWorldState state, GoodsParticipant seller, GoodsParticipant buyer, long now) {
        return book(state, now).offers(buyer.party(), Optional.of(seller.party()), Set.of());
    }
    static List<Endpoints> endpointPairs(FrontierWorldState state, GoodsParticipant seller, GoodsParticipant buyer, long now) {
        return quotes(state, seller, buyer, now).stream()
                .filter(pair -> pair.sell().unitPriceLimit().compareTo(pair.buy().unitPriceLimit()) <= 0)
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
        return first(state, reviewer, now, selectedSeller, selectedBuyer, excludedCommodities, endpoints, FixedScalar.ZERO);
    }
    static Search first(FrontierWorldState state, GoodsParticipant reviewer, long now,
                        Optional<GoodsTradeParty> selectedSeller, Optional<GoodsTradeParty> selectedBuyer,
                        Set<String> excludedCommodities, Optional<Endpoints> endpoints, FixedScalar unreservedDeliveryExpense) {
        if (unreservedDeliveryExpense.raw() < 0) throw new IllegalArgumentException("negative anticipated delivery expense");
        var trade = state.companies().goodsTrade();
        var book = book(state, now);
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
                var buyer = trade.participants().participants().get(buy.party().id());
                if (buyer == null || !buyer.party().equals(buy.party()) || !known(seller, buyer) || !known(buyer, seller)) continue;
                if (sell.unitPriceLimit().compareTo(buy.unitPriceLimit()) > 0) { disposition = "PRICE_LIMIT"; continue; }
                long availableCash = state.inventory().economics().availableToReserve(buyer.party().id()).raw();
                if (sell.unitPriceLimit().raw() > Math.max(0, availableCash - unreservedDeliveryExpense.raw())) {
                    disposition = "INSUFFICIENT_PROCUREMENT_FUNDS"; continue;
                }
                if (!GoodsParticipantStorage.eligible(state, seller, sell.containerId(), sell.itemKind())
                        || !GoodsParticipantStorage.eligible(state, buyer, buy.containerId(), buy.itemKind())) {
                    disposition = "STORAGE_UNAVAILABLE"; continue;
                }
                // Reject a physically empty or full endpoint before evaluating
                // either participant's complete production/funding policy. This
                // is only a necessary condition; exact allocations, authority
                // and the complete quantity are still validated below.
                if (GoodsParticipantStorage.unclaimed(state, seller, sell.containerId(), sell.itemKind()) == 0) {
                    disposition = "SOURCE_ALLOCATION_OR_PHYSICAL_AUTHORITY"; continue;
                }
                if (!sell.containerId().equals(buy.containerId())
                        && !GoodsTradeStateSupport.receivingCapacity(state, buy.containerId(), buy.itemKind(), 1)) {
                    disposition = "RECEIVER_CAPACITY"; continue;
                }
                long funding = GoodsParticipantFunding.purchase(GoodsParticipantAssessment.trading(state, buyer, now),
                        state.bootstrap().ruleset().goodsTrade(), buy.itemKind()).raw();
                funding = Math.max(0, funding - unreservedDeliveryExpense.raw());
                if (funding < sell.unitPriceLimit().raw()) { disposition = "INSUFFICIENT_PROCUREMENT_FUNDS"; continue; }
                var buyerIntent = intent(state, buyer, buy.itemKind(), GoodsTradeOrder.Side.BUY, now, cachedIntents);
                if (buyerIntent.isEmpty()) { disposition = "CURRENT_RESERVE_STOCK_OR_FUNDS"; continue; }
                var sellerIntent = intent(state, seller, sell.itemKind(), GoodsTradeOrder.Side.SELL, now, cachedIntents);
                if (sellerIntent.isEmpty()) { disposition = "CURRENT_RESERVE_STOCK_OR_FUNDS"; continue; }
                if (sell.unitPriceLimit().compareTo(sellerIntent.orElseThrow().limit()) < 0
                        || sell.unitPriceLimit().compareTo(buyerIntent.orElseThrow().limit()) > 0) {
                    disposition = "CURRENT_PRICE_POLICY"; continue;
                }
                // Quote matching owns price/stock/funds only. The selected logistics
                // candidate separately owns connectivity, crew and departure proof.
                int max = Math.min(Math.min(sell.availableQuantity(), buy.availableQuantity()),
                        Math.min(sellerIntent.orElseThrow().quantity(), buyerIntent.orElseThrow().quantity()));
                FixedScalar price = sell.unitPriceLimit();
                max = (int) Math.min(max, funding / price.raw());
                if (GoodsStockSpecification.forKind(state.bootstrap().ruleset().goodsTrade(), sell.itemKind()).representation()
                        == GoodsStockSpecification.Representation.INDIVIDUAL) {
                    if (max < 1 || !GoodsTradeStateSupport.receivingCapacity(state, buy.containerId(), buy.itemKind(), 1)
                            || ReferenceContainerCustody.blocksCanonicalUse(state, sell.containerId())
                            || ReferenceContainerCustody.hasLiveCustody(state, sell.containerId())
                                && !ReferenceContainerCustody.hasOperationalCustody(state, sell.containerId())) continue;
                    var stock = GoodsParticipantStorage.individualStock(state, seller, sell.containerId(), sell.itemKind());
                    if (stock.isEmpty()) { disposition = "NO_ELIGIBLE_INDIVIDUAL_STOCK"; continue; }
                    var item = stock.getFirst();
                    String stem = WorkOpportunityIdentity.digest(sell.id().value() + "|" + buy.id().value()
                            + "|" + sell.committedQuantity() + "|" + buy.committedQuantity() + "|" + item.id().value());
                    var contractId = new SubjectId("contract:goods/" + stem); var claimId = new SubjectId("claim:goods/" + stem);
                    var allocation = new IndividualResourceAllocation(claimId, contractId, sell.party().id(), item.id(), item.itemKind(), item.condition());
                    var contract = new GoodsTradeContract(contractId, sell.id(), buy.id(), sell.party(), buy.party(),
                            new GoodsTradeFulfillment(GoodsTradeFulfillment.Mode.SELLER_DELIVERY, sell.party()), sell.containerId(), buy.containerId(),
                            sell.itemKind(), 1, price, new SubjectId("reservation:goods/" + stem), 0, Map.of(claimId, 1), Map.of(), Map.of(), 0, 0,
                            FixedScalar.ZERO, Map.of(claimId, allocation));
                    return new Search(Optional.of(new Match(contract, List.of())), "MATCH", checked);
                }
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
                                                         String itemKind, GoodsTradeOrder.Side side, long now) {
        return intent(state, participant, itemKind, side, now, new HashMap<>());
    }
    private static Optional<GoodsParticipantPolicy.Intent> intent(FrontierWorldState state, GoodsParticipant participant,
            String itemKind, GoodsTradeOrder.Side side, long now, Map<SubjectId, List<GoodsParticipantPolicy.Intent>> cache) {
        return cache.computeIfAbsent(participant.party().id(), ignored -> GoodsParticipantPolicies.require(participant)
                .decide(GoodsParticipantAssessment.trading(state, participant, now), state.bootstrap().ruleset().goodsTrade()))
                .stream().filter(value -> value.itemKind().equals(itemKind) && value.side() == side).findFirst();
    }
    private static boolean known(GoodsParticipant owner, GoodsParticipant other) {
        return owner.known().stream().anyMatch(peer -> peer.party().equals(other.party()) && peer.settlementId().equals(other.settlementId()));
    }
    private GoodsOrderMatching() { }
}
