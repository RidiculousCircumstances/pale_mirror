package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Exact policy/endpoint/known-peer declaration. Knowledge does not grant consent or stock access. */
public record GoodsParticipant(GoodsTradeParty party, GoodsPolicyKind policy, ShipmentEndpoint endpoint,
                               List<Counterparty> known, long reviewRevision, String decision, long reviewedAtTick) {
    public record Counterparty(GoodsTradeParty party, ShipmentEndpoint endpoint) {
        public Counterparty { Objects.requireNonNull(party); Objects.requireNonNull(endpoint); }
    }
    public GoodsParticipant {
        Objects.requireNonNull(party); Objects.requireNonNull(policy); Objects.requireNonNull(endpoint);
        policy.validate(party);
        known = List.copyOf(known);
        if (known.size() > 16 || known.stream().map(value -> value.party().id()).distinct().count() != known.size()
                || known.stream().anyMatch(value -> value.party().equals(party)) || reviewRevision < 0 || reviewedAtTick < 0
                || decision == null || decision.isBlank() || decision.length() > 512)
            throw new IllegalArgumentException("invalid bounded participant knowledge/review");
    }
    public GoodsParticipant reviewed(String reason, long tick) {
        if (tick < reviewedAtTick) throw new IllegalArgumentException("participant review went backwards");
        return new GoodsParticipant(party, policy, endpoint, known, Math.addExact(reviewRevision, 1), reason, tick);
    }
}
