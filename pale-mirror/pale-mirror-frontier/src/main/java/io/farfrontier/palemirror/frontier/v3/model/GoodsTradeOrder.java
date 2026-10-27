package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Participant-owned public consent. Matching names exact counterparties in a contract. */
public record GoodsTradeOrder(SubjectId id, GoodsTradeParty party,
                              Side side, SubjectId containerId, String itemKind, int quantity,
                              int committedQuantity, FixedScalar unitPriceLimit, long expiresAtTick, int retiredCommittedQuantity) {
    /** A newly issued offer has no retired contract history. Hydration supplies it explicitly. */
    public GoodsTradeOrder(SubjectId id, GoodsTradeParty party, Side side, SubjectId containerId, String itemKind,
            int quantity, int committedQuantity, FixedScalar unitPriceLimit, long expiresAtTick) {
        this(id, party, side, containerId, itemKind, quantity, committedQuantity, unitPriceLimit, expiresAtTick, 0);
    }
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
        Objects.requireNonNull(id); Objects.requireNonNull(party);
        Objects.requireNonNull(side); Objects.requireNonNull(containerId); Objects.requireNonNull(unitPriceLimit);
        if (quantity < 1 || quantity > ResourceLot.MAX_QUANTITY
                || committedQuantity < 0 || committedQuantity > quantity || retiredCommittedQuantity < 0 || retiredCommittedQuantity > committedQuantity || unitPriceLimit.raw() <= 0
                || expiresAtTick < 0 || itemKind == null
                || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")) {
            throw new IllegalArgumentException("invalid goods order authorization");
        }
        unitPriceLimit.multiply(quantity);
    }
    public int availableQuantity() { return quantity - committedQuantity; }
    public GoodsTradeOrder commit(int count) {
        if (count < 1 || count > availableQuantity()) throw new IllegalArgumentException("goods order is overcommitted");
        return new GoodsTradeOrder(id, party, side, containerId, itemKind, quantity,
                Math.addExact(committedQuantity, count), unitPriceLimit, expiresAtTick, retiredCommittedQuantity);
    }
    public GoodsTradeOrder retireCommitment(int quantity) {
        if (quantity < 1 || quantity > committedQuantity - retiredCommittedQuantity)
            throw new IllegalArgumentException("retired contract exceeds this order's retained commitment");
        return new GoodsTradeOrder(id, party, side, containerId, itemKind, this.quantity, committedQuantity,
                unitPriceLimit, expiresAtTick, Math.addExact(retiredCommittedQuantity, quantity));
    }

}
