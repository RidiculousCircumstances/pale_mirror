package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact buyer requirement; it owns neither money, item custody nor a seller decision. */
record MarketDemand(SubjectId id, SubjectId buyerId, SubjectId reasonId, String itemKind, int itemCount,
                    FixedScalar maximumTotalPrice, long openedAtTick, long expiresAtTick, MarketDemandStatus status) {
    MarketDemand {
        Objects.requireNonNull(id, "market demand id"); Objects.requireNonNull(buyerId, "market demand buyer");
        Objects.requireNonNull(reasonId, "market demand reason"); Objects.requireNonNull(itemKind, "market demand item kind");
        Objects.requireNonNull(maximumTotalPrice, "market demand maximum price"); Objects.requireNonNull(status, "market demand status");
        if (!id.value().startsWith("demand:") || itemKind.isBlank() || itemCount <= 0 || maximumTotalPrice.raw() <= 0L
                || openedAtTick < 0L || expiresAtTick < openedAtTick) {
            throw new IllegalArgumentException("market demand must retain a named positive bounded need");
        }
    }

    MarketDemand withStatus(MarketDemandStatus next) {
        return new MarketDemand(id, buyerId, reasonId, itemKind, itemCount, maximumTotalPrice, openedAtTick, expiresAtTick, next);
    }
}
