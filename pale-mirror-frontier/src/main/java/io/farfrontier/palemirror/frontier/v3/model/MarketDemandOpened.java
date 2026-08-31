package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** Durable creation of one buyer need; only the scheduled market process emits it. */
public record MarketDemandOpened(MarketDemand demand) implements FrontierPayload {
    public MarketDemandOpened { Objects.requireNonNull(demand, "market demand"); }
    @Override public String type() { return "frontier.market_demand_opened"; }
}
