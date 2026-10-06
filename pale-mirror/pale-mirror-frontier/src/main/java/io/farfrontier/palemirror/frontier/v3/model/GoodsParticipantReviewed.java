package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

public record GoodsParticipantReviewed(long expectedRevision, String decision, long atTick) implements FrontierPayload {
    public GoodsParticipantReviewed {
        if (expectedRevision < 0 || atTick < 0 || decision == null || decision.isBlank() || decision.length() > 512)
            throw new IllegalArgumentException("invalid participant review receipt");
    }
    @Override public String type() { return "frontier.goods_participant_reviewed"; }
}
