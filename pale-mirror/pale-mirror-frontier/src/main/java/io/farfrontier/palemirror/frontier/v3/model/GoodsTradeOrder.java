package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Participant-owned consent to trade with one explicitly known counterparty. */
public record GoodsTradeOrder(SubjectId id, GoodsTradeParty party, GoodsTradeParty counterparty,
                              Side side, SubjectId containerId, String itemKind, int quantity,
                              int committedQuantity, FixedScalar unitPriceLimit, long expiresAtTick) {
    public enum Side {
        BUY(1), SELL(2);
        private final int tag;
        Side(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Side fromWireTag(int tag) {
            for (Side side : values()) if (side.tag == tag) return side;
            throw new IllegalArgumentException("unknown goods order side");
        }
    }
    public GoodsTradeOrder {
        Objects.requireNonNull(id); Objects.requireNonNull(party); Objects.requireNonNull(counterparty);
        Objects.requireNonNull(side); Objects.requireNonNull(containerId); Objects.requireNonNull(unitPriceLimit);
        if (party.id().equals(counterparty.id()) || quantity < 1 || quantity > ResourceLot.MAX_QUANTITY
                || committedQuantity < 0 || committedQuantity > quantity || unitPriceLimit.raw() <= 0
                || expiresAtTick < 0 || itemKind == null
                || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")) {
            throw new IllegalArgumentException("invalid goods order authorization");
        }
        unitPriceLimit.multiply(quantity);
    }
    public int availableQuantity() { return quantity - committedQuantity; }
    public GoodsTradeOrder commit(int count) {
        if (count < 1 || count > availableQuantity()) throw new IllegalArgumentException("goods order is overcommitted");
        return new GoodsTradeOrder(id, party, counterparty, side, containerId, itemKind, quantity,
                Math.addExact(committedQuantity, count), unitPriceLimit, expiresAtTick);
    }
}
