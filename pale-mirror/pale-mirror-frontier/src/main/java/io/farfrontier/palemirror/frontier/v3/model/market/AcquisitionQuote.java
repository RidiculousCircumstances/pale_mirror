package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.Objects;

/** Provisional per-item purchase cost, not a funded contract or a route guarantee. */
public record AcquisitionQuote(FixedScalar goods, FixedScalar delivery) {
    public AcquisitionQuote {
        Objects.requireNonNull(goods); Objects.requireNonNull(delivery);
        if (goods.raw() <= 0 || delivery.raw() < 0) throw new IllegalArgumentException("invalid acquisition quote");
    }
    public FixedScalar total() { return goods.plus(delivery); }
}
