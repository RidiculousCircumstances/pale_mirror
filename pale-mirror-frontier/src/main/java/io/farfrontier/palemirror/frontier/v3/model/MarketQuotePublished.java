package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** Durable publication of one current seller offer. */
public record MarketQuotePublished(CompanyQuote quote) implements FrontierPayload {
    public MarketQuotePublished { Objects.requireNonNull(quote, "market quote"); }
    @Override public String type() { return "frontier.market_quote_published"; }
}
