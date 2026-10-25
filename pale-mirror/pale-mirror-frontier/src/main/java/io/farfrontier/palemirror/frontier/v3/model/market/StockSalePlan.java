package io.farfrontier.palemirror.frontier.v3.model.market;

import java.util.Arrays;

/** Declared purpose and exposure bound of an existing-stock offer; never production authority. */
public record StockSalePlan(Kind kind, long quantityLimit, long cashRequirement) {
    public enum Kind {
        RETAINED_MARGIN(1), OPERATING_RECOVERY(2);
        private final int tag;
        Kind(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Kind fromWireTag(int tag) { return Arrays.stream(values()).filter(k -> k.tag == tag).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown stock sale purpose: " + tag)); }
    }
    public StockSalePlan {
        java.util.Objects.requireNonNull(kind);
        if (quantityLimit < 0 || cashRequirement < 0
                || kind == Kind.RETAINED_MARGIN && (quantityLimit != 0 || cashRequirement != 0)
                || kind == Kind.OPERATING_RECOVERY && (quantityLimit < 1 || cashRequirement < 1))
            throw new IllegalArgumentException("invalid stock sale scope");
    }
    public static StockSalePlan retained() { return new StockSalePlan(Kind.RETAINED_MARGIN, 0, 0); }
}
