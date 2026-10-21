package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** Exact outcome of a retained endpoint effect after its physical carrier died. */
public record ShipmentRetiredEffectObserved(ShipmentHotTransferred effect, boolean applied) implements FrontierPayload {
    public ShipmentRetiredEffectObserved {
        Objects.requireNonNull(effect);
        if (!applied && (!effect.remainingSource().isEmpty() || !effect.destination().isEmpty()))
            throw new IllegalArgumentException("unused shipment effect cannot claim transferred stock");
    }
    @Override public String type() { return "frontier.shipment_retired_effect_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
