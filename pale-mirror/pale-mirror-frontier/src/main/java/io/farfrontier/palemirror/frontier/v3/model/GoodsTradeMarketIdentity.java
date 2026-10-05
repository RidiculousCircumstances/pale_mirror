package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Explicit process identity, not a resource owner or a financial account. */
public final class GoodsTradeMarketIdentity {
    public static final SubjectId OWNER = new SubjectId("market:goods");
    private GoodsTradeMarketIdentity() { }
}
