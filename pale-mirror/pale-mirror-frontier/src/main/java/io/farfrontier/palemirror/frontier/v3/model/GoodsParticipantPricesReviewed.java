package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.market.MarketPricePolicy;
import java.util.Map;

public record GoodsParticipantPricesReviewed(long expectedRevision, long atTick,
                                             Map<String, MarketPricePolicy.Quote> prices) implements FrontierPayload {
    public GoodsParticipantPricesReviewed {
        prices = Map.copyOf(prices);
        if (expectedRevision < 0 || atTick < 0 || prices.isEmpty() || prices.size() > 16)
            throw new IllegalArgumentException("invalid participant price review");
    }
    @Override public String type() { return "frontier.goods_participant_prices_reviewed"; }
}
