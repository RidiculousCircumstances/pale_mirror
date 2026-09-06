package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Bounded scheduled terminal fact for a demand whose deadline passed unfilled. */
public record MarketDemandExpired(SubjectId demandId) implements FrontierPayload {
    public MarketDemandExpired { Objects.requireNonNull(demandId, "market demand id"); }
    @Override public String type() { return "frontier.market_demand_expired"; }
}
