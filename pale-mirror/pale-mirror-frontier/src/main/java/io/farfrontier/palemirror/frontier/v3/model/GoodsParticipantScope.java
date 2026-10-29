package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Explicit read scope of one declared participant; review text/prices are not its identity. */
record GoodsParticipantScope(GoodsTradeParty party, GoodsPolicyKind policy, SubjectId home) {
    static GoodsParticipantScope of(GoodsParticipant participant) {
        return new GoodsParticipantScope(participant.party(), participant.policy(), participant.settlementId());
    }
}
