package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Bounded participant decisions, never a second stock, money or task ledger. */
public record GoodsParticipantState(Map<SubjectId, GoodsParticipant> participants,
        Map<SubjectId, SettlementEconomicPlanningState> planning) {
    public static final int MAX_PARTICIPANTS = 2048;
    public GoodsParticipantState(Map<SubjectId, GoodsParticipant> participants) { this(participants, Map.of()); }
    public GoodsParticipantState {
        participants = ImmutableLookupMap.copyOf(participants); planning = ImmutableLookupMap.copyOf(planning);
        if (participants.size() > MAX_PARTICIPANTS || participants.entrySet().stream()
                .anyMatch(e -> !e.getKey().equals(e.getValue().party().id()))
                || !participants.keySet().containsAll(planning.keySet()))
            throw new IllegalArgumentException("invalid goods participant registry");
    }
    public static GoodsParticipantState empty() { return new GoodsParticipantState(Map.of()); }
    public GoodsParticipantState register(GoodsParticipant participant) {
        if (participants.containsKey(participant.party().id())) throw new IllegalArgumentException("duplicate goods participant");
        return new GoodsParticipantState(ImmutableLookupMap.withEntry(participants, participant.party().id(), participant), planning);
    }
    public GoodsParticipantState reviewed(SubjectId id, long revision, String decision, long tick) {
        var current = participants.get(id);
        if (current == null || current.reviewRevision() != revision) throw new IllegalArgumentException("stale participant review");
        return new GoodsParticipantState(ImmutableLookupMap.withEntry(participants, id, current.reviewed(decision, tick)), planning);
    }
    public GoodsParticipantState prices(SubjectId id, GoodsParticipantPricesReviewed review) {
        var current = participants.get(id);
        if (current == null || current.reviewRevision() != review.expectedRevision() || review.atTick() < current.reviewedAtTick()
                || current.prices().equals(review.prices())) throw new IllegalArgumentException("stale or repeated participant price review");
        return new GoodsParticipantState(ImmutableLookupMap.withEntry(participants, id, current.withPrices(review.prices())), planning);
    }
    public SettlementEconomicPlanningState economicState(SubjectId id) {
        if (!participants.containsKey(id)) throw new IllegalArgumentException("unknown economic participant");
        return planning.getOrDefault(id, SettlementEconomicPlanningState.empty());
    }
    public GoodsParticipantState withEconomicState(SubjectId id, SettlementEconomicPlanningState value) {
        return withEconomicStates(Map.of(id, value));
    }
    /** One immutable publication of independent participant changes; no intermediate
     * full registry copies or partially advanced owner revisions. */
    public GoodsParticipantState withEconomicStates(Map<SubjectId, SettlementEconomicPlanningState> changes) {
        changes = Map.copyOf(changes);
        if (!participants.keySet().containsAll(changes.keySet())) throw new IllegalArgumentException("unknown economic participant");
        if (changes.isEmpty()) return this;
        if (changes.size() == 1) {
            var entry = changes.entrySet().iterator().next();
            return new GoodsParticipantState(participants, ImmutableLookupMap.withEntry(planning, entry.getKey(), entry.getValue()));
        }
        var next = new HashMap<>(planning); next.putAll(changes);
        return new GoodsParticipantState(participants, next);
    }
}
