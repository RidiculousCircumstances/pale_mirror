package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Bounded participant decisions, never a second stock, money or task ledger. */
public record GoodsParticipantState(Map<SubjectId, GoodsParticipant> participants,
        Map<SubjectId, SettlementEconomicPlanningState> planning) {
    public static final int MAX_PARTICIPANTS = 2048;
    public GoodsParticipantState(Map<SubjectId, GoodsParticipant> participants) { this(participants, Map.of()); }
    public GoodsParticipantState {
        participants = Map.copyOf(participants); planning = Map.copyOf(planning);
        if (participants.size() > MAX_PARTICIPANTS || participants.entrySet().stream()
                .anyMatch(e -> !e.getKey().equals(e.getValue().party().id()))
                || !participants.keySet().containsAll(planning.keySet()))
            throw new IllegalArgumentException("invalid goods participant registry");
    }
    public static GoodsParticipantState empty() { return new GoodsParticipantState(Map.of()); }
    public GoodsParticipantState register(GoodsParticipant participant) {
        if (participants.containsKey(participant.party().id())) throw new IllegalArgumentException("duplicate goods participant");
        var next = new HashMap<>(participants); next.put(participant.party().id(), participant);
        return new GoodsParticipantState(next, planning);
    }
    public GoodsParticipantState reviewed(SubjectId id, long revision, String decision, long tick) {
        var current = participants.get(id);
        if (current == null || current.reviewRevision() != revision) throw new IllegalArgumentException("stale participant review");
        var next = new HashMap<>(participants); next.put(id, current.reviewed(decision, tick));
        return new GoodsParticipantState(next, planning);
    }
    public GoodsParticipantState prices(SubjectId id, GoodsParticipantPricesReviewed review) {
        var current = participants.get(id);
        if (current == null || current.reviewRevision() != review.expectedRevision() || review.atTick() < current.reviewedAtTick()
                || current.prices().equals(review.prices())) throw new IllegalArgumentException("stale or repeated participant price review");
        var next = new HashMap<>(participants); next.put(id, current.withPrices(review.prices()));
        return new GoodsParticipantState(next, planning);
    }
    public SettlementEconomicPlanningState economicState(SubjectId id) {
        if (!participants.containsKey(id)) throw new IllegalArgumentException("unknown economic participant");
        return planning.getOrDefault(id, SettlementEconomicPlanningState.empty());
    }
    public GoodsParticipantState withEconomicState(SubjectId id, SettlementEconomicPlanningState value) {
        if (!participants.containsKey(id)) throw new IllegalArgumentException("unknown economic participant");
        var next = new HashMap<>(planning); next.put(id, Objects.requireNonNull(value));
        return new GoodsParticipantState(participants, next);
    }
}
