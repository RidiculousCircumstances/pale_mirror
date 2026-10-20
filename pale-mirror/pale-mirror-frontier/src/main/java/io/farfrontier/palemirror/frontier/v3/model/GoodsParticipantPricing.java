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
        var participant = Objects.requireNonNull(state.companies().goodsTrade().participants().participants().get(subject));
        if (!event.prices().equals(review(state, participant, event.atTick())))
            throw new IllegalArgumentException("price proposal differs from its authorized participant policy");
        return applyCommitted(state, subject, event, now);
    }
    public static FrontierWorldState applyCommitted(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId subject,
                                                    GoodsParticipantPricesReviewed event, long now) {
        var trade = state.companies().goodsTrade();
        var participant = Objects.requireNonNull(trade.participants().participants().get(subject), "price policy participant");
        // The scheduled participant owner calculated this committed decision.
        // Recovery validates its envelope, not a new pricing strategy against
        // historical facts. No external command can submit this event family.
        var catalog = state.bootstrap().ruleset().goodsTrade().policies().get(participant.policy()).stream()
                .map(GoodsTradeRules.Commodity::itemKind).collect(java.util.stream.Collectors.toSet());
        if (event.atTick() > now || !event.prices().keySet().equals(catalog)
                || event.prices().entrySet().stream().anyMatch(entry -> entry.getValue().reviewedAt() > event.atTick()
                    || Optional.ofNullable(participant.prices().get(entry.getKey()))
                        .filter(old -> entry.getValue().reviewedAt() < old.reviewedAt()).isPresent()))
            throw new IllegalArgumentException("price review has a foreign catalog or time boundary");
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
        var assessment = GoodsParticipantAssessment.domestic(state, participant, now);
        var view = assessment.view();
        var history = state.companies().goodsTrade().priceHistory();
        var next = new TreeMap<String, MarketPricePolicy.Quote>();
        var policy = new MarketPricePolicy();
        var prices = new TreeMap<String, io.farfrontier.palemirror.frontier.v3.api.FixedScalar>();
        catalog.forEach(commodity -> prices.put(commodity.itemKind(), rules.supplyPlanning().sourcing().isPresent()
                ? history.reference(commodity.itemKind(), rules.pricing().recencyTicks()).orElse(commodity.minimumSellPrice())
                : ask(participant, commodity)));
        var floors = GoodsProductionPricing.floors(state.bootstrap().ruleset().production(), prices);
        var production = GoodsProductionPricing.assess(assessment, prices);
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
            long surplus = view.saleable(commodity);
            var acquisition = GoodsAcquisitionPricing.purchase(state, participant, commodity.itemKind(), shortage, now,
                    GoodsParticipantFunding.purchase(view, rules, commodity.itemKind()));
            var context = new MarketPricePolicy.Context(floors.getOrDefault(commodity.itemKind(), io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO),
                    Optional.ofNullable(production.alternatives().get(commodity.itemKind())), pressure,
                    GoodsMarketDemand.read(state, participant, commodity.itemKind(), surplus, now),
                    acquisition, view.supplyPlan().isPresent() && rules.supplyPlanning().sourcing().isPresent()
                            ? Optional.of(GoodsEconomicPricing.assess(state, view, commodity.itemKind(), prices, production, acquisition, now))
                            : Optional.empty());
            next.put(commodity.itemKind(), policy.review(commodity.minimumSellPrice(), commodity.maximumBuyPrice(), reference,
                    surplus, sale, Optional.ofNullable(participant.prices().get(commodity.itemKind())), now, rules.pricing(), context));
        }
        return Map.copyOf(next);
    }
}
