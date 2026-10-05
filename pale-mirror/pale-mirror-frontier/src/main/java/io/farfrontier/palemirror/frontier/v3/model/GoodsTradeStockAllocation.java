package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Producer declares stock authority; missing physical evidence never implies COLD permission. */
public record GoodsTradeStockAllocation(SubjectId accountId, ClaimAllocation claim, Authority authority, long epoch) {
    public enum Authority {
        COLD(1), PHYSICAL(2);
        private final int tag;
        Authority(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Authority fromWireTag(int tag) {
            for (Authority value : values()) if (value.tag == tag) return value;
            throw new IllegalArgumentException("unknown goods stock authority");
        }
    }
    public GoodsTradeStockAllocation {
        Objects.requireNonNull(accountId); Objects.requireNonNull(claim); Objects.requireNonNull(authority);
        if (claim.purpose() != ClaimPurpose.GOODS_TRADE || claim.lotQuantities().isEmpty()
                || authority == Authority.COLD && epoch != 0 || authority == Authority.PHYSICAL && epoch < 1) {
            throw new IllegalArgumentException("invalid exact goods stock allocation");
        }
    }
}
