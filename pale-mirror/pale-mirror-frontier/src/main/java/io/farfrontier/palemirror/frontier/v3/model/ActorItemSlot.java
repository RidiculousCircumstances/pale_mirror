package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** A physical presentation location, not an owner, resource kind or activity. */
public sealed interface ActorItemSlot permits ActorItemSlot.Hand, ActorItemSlot.Pocket {
    record Hand(ActorContainerItemOrder.Hand hand) implements ActorItemSlot {
        public Hand { Objects.requireNonNull(hand, "actor hand"); }
    }
    record Pocket(int index) implements ActorItemSlot {
        public Pocket {
            if (index < 0 || index >= 8) throw new IllegalArgumentException("actor pocket outside inventory");
        }
    }
}
