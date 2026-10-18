package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

/** Deterministic review command in the WAL; replay uses the same hashed rules and exact predecessor state. */
public record CultivationReviewed(long previousReviewTick, long atTick) implements FrontierPayload {
    public static final String TYPE = "frontier.cultivation_reviewed";
    public CultivationReviewed {
        if (previousReviewTick < -1 || atTick <= previousReviewTick) throw new IllegalArgumentException("invalid cultivation clock");
    }
    @Override public String type() { return TYPE; }
}
