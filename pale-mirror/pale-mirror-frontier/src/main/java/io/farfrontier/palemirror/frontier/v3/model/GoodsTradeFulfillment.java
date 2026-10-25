package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Immutable organizer and acceptance boundary agreed before goods/money reservations. */
public record GoodsTradeFulfillment(Mode mode, GoodsTradeParty organizer) {
    public enum Mode {
        SELLER_DELIVERY(1), BUYER_COLLECTION(2);
        private final int tag;
        Mode(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Mode fromWireTag(int tag) {
            return switch (tag) { case 1 -> SELLER_DELIVERY; case 2 -> BUYER_COLLECTION;
                default -> throw new IllegalArgumentException("unknown fulfillment mode tag"); };
        }
    }
    public GoodsTradeFulfillment { Objects.requireNonNull(mode); Objects.requireNonNull(organizer); }
    public void require(GoodsTradeParty seller, GoodsTradeParty buyer) {
        if (!organizer.equals(mode == Mode.SELLER_DELIVERY ? seller : buyer))
            throw new IllegalArgumentException("commercial fulfillment has a foreign declared organizer");
    }
}
