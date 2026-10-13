package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Immutable organizer and acceptance boundary agreed before goods/money reservations. */
public record GoodsTradeFulfillment(Mode mode, GoodsTradeParty organizer) {
    public enum Mode { SELLER_DELIVERY, BUYER_COLLECTION }
    public GoodsTradeFulfillment { Objects.requireNonNull(mode); Objects.requireNonNull(organizer); }
    public void require(GoodsTradeParty seller, GoodsTradeParty buyer) {
        if (!organizer.equals(mode == Mode.SELLER_DELIVERY ? seller : buyer))
            throw new IllegalArgumentException("commercial fulfillment has a foreign declared organizer");
    }
}
