package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;

/** Durable canonical commitment made during COLD resolution, before any later world visit. */
public record DeferredAftermathPrepared(DeferredAftermath aftermath) implements FrontierPayload {
    public DeferredAftermathPrepared { Objects.requireNonNull(aftermath, "aftermath"); }
    @Override public String type() { return "frontier.deferred_aftermath_prepared"; }
}
