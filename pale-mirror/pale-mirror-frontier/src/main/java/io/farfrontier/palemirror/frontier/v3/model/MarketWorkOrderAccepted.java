package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** Accepts one exact quote before the matching production job is durably opened. */
public record MarketWorkOrderAccepted(MarketWorkOrder order) implements FrontierPayload {
    public MarketWorkOrderAccepted { Objects.requireNonNull(order, "market work order"); }
    @Override public String type() { return "frontier.market_work_order_accepted"; }
}
