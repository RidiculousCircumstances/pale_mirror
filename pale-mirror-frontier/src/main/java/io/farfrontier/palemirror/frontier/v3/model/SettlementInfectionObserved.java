package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable local observation; a zero intensity withdraws a formerly known local fact. */
public record SettlementInfectionObserved(SubjectId settlementId, InfectionCell cell, FixedRatio intensity, long observedAt) implements FrontierPayload {
    public SettlementInfectionObserved {
        Objects.requireNonNull(settlementId, "observing settlement"); Objects.requireNonNull(cell, "observed cell"); Objects.requireNonNull(intensity, "observed intensity");
        if (observedAt < 0L) throw new IllegalArgumentException("observation tick must be non-negative");
    }
    @Override public String type() { return "frontier.settlement_infection_observed"; }
}
