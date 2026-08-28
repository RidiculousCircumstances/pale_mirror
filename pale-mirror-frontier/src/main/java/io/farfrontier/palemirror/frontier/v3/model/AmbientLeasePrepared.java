package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** Durable-before-body admission for one exact ambient HOT actor. */
public record AmbientLeasePrepared(AmbientActorLease lease) implements FrontierPayload {
    public AmbientLeasePrepared { Objects.requireNonNull(lease, "ambient lease"); }
    @Override public String type() { return "frontier.ambient_lease_prepared"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
