package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.market.*;
import java.util.*;

/** Participant policy adapter; the pure pricing mechanism knows no world, job or storage layout. */
public final class GoodsParticipantPricing {
    private GoodsParticipantPricing() { }
    public static io.farfrontier.palemirror.frontier.v3.api.FixedScalar ask(GoodsParticipant participant, GoodsTradeRules.Commodity commodity) {
        var quote = participant.prices().get(commodity.itemKind());
        return quote == null ? commodity.minimumSellPrice() : quote.ask();
    }
    public static io.farfrontier.palemirror.frontier.v3.api.FixedScalar bid(GoodsParticipant participant, GoodsTradeRules.Commodity commodity) {
        var quote = participant.prices().get(commodity.itemKind());
        return quote == null ? commodity.maximumBuyPrice() : quote.bid();
    }
    public static FrontierWorldState apply(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                           GoodsParticipantPricesReviewed event, long now) {
        var trade = state.companies().goodsTrade();
        var participant = Objects.requireNonNull(trade.participants().participants().get(subject), "price policy participant");
        if (event.atTick() > now || !event.prices().equals(review(state, participant, event.atTick())))
            throw new IllegalArgumentException("price review differs from its authorized participant policy");
        return state.withCompanies(state.companies().withGoodsTrade(trade.withParticipants(trade.participants().prices(subject, event))));
    }
    public static Map<String, MarketPricePolicy.Quote> review(FrontierWorldState state, GoodsParticipant participant, long now) {
        var rules = state.bootstrap().ruleset().goodsTrade();
        if (!rules.pricing().enabled()) return participant.prices();
        var catalog = rules.policies().get(participant.policy());
        if (participant.prices().keySet().equals(catalog.stream().map(GoodsTradeRules.Commodity::itemKind)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()))
                && participant.prices().values().stream().allMatch(quote -> now >= quote.reviewedAt()
                    && now - quote.reviewedAt() < rules.pricing().reviewTicks())) return participant.prices();
        var view = GoodsParticipantView.read(state, participant);
        var history = state.companies().goodsTrade().priceHistory();
        var next = new TreeMap<String, MarketPricePolicy.Quote>();
        var policy = new MarketPricePolicy();
        var prices = new TreeMap<String, io.farfrontier.palemirror.frontier.v3.api.FixedScalar>();
        catalog.forEach(commodity -> prices.put(commodity.itemKind(), ask(participant, commodity)));
        var floors = GoodsProductionPricing.floors(state.bootstrap().ruleset().production(), prices);
        var alternatives = GoodsProductionPricing.alternatives(state, view, prices);
        for (var commodity : catalog) {
            var stock = view.stocks().get(commodity.itemKind());
            var reference = history.reference(commodity.itemKind(), rules.pricing().recencyTicks()).orElse(commodity.minimumSellPrice());
            var sale = history.commodities().getOrDefault(commodity.itemKind(), List.of()).stream()
                    .filter(value -> value.seller().equals(participant.party().id())).reduce((first, last) -> last)
                    .map(MarketPriceHistory.Sale::receipt);
            int target = view.target(commodity);
            long shortage = view.supplyPlan().map(plan -> plan.require(commodity.itemKind()).purchase())
                    .orElse((long) Math.max(0, target - stock.unclaimedInStorage() - stock.expectedIncoming()));
            int pressure = (int) Math.min(1000, Math.multiplyExact(shortage, 1000) / Math.max(1, target));
            var context = new MarketPricePolicy.Context(floors.getOrDefault(commodity.itemKind(), io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO),
                    Optional.ofNullable(alternatives.get(commodity.itemKind())), pressure,
                    GoodsParticipantStorage.containers(state, participant, commodity.itemKind()).stream()
                            .anyMatch(container -> TransportMarketAccess.confirmed(state, container, commodity.itemKind())));
            next.put(commodity.itemKind(), policy.review(commodity.minimumSellPrice(), commodity.maximumBuyPrice(), reference,
                    Math.max(0, stock.unclaimedInStorage() - target), sale, Optional.ofNullable(participant.prices().get(commodity.itemKind())), now, rules.pricing(), context));
        }
        return Map.copyOf(next);
    }
}
