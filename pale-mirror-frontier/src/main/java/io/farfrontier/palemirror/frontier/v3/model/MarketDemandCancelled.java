package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable terminal refusal of an open demand before any work order exists. */
public record MarketDemandCancelled(SubjectId demandId, MarketDemandCancellationReason reason) implements FrontierPayload {
    public MarketDemandCancelled {
        Objects.requireNonNull(demandId, "market cancellation demand");
        Objects.requireNonNull(reason, "market cancellation reason");
    }

    @Override public String type() { return "frontier.market_demand_cancelled"; }
}
