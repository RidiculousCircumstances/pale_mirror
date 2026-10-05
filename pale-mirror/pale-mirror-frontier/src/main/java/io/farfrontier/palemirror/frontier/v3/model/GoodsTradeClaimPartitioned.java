package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

public record GoodsTradeClaimPartitioned(SubjectId contractId, ResourceClaimPartition partition) implements FrontierPayload {
    public GoodsTradeClaimPartitioned { Objects.requireNonNull(contractId); Objects.requireNonNull(partition); }
    @Override public String type() { return "frontier.goods_trade_claim_partitioned"; }
}
