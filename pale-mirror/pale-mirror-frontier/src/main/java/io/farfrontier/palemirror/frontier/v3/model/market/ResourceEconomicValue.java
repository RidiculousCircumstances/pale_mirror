package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.Objects;

/** Owner-valued retained stock and delivered acquisition, not money or a contract.
 * Production opportunity value is kept separate from the observed market price. */
public record ResourceEconomicValue(FixedScalar retained, FixedScalar acquisition) {
    public ResourceEconomicValue {
        Objects.requireNonNull(retained); Objects.requireNonNull(acquisition);
        if (retained.raw() < 0 || acquisition.raw() <= 0) throw new IllegalArgumentException("invalid resource value");
    }
}
