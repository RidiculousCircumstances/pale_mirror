package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Bounded participant decisions, never a second stock, money or task ledger. */
public record GoodsParticipantState(Map<SubjectId, GoodsParticipant> participants) {
    public static final int MAX_PARTICIPANTS = 2048;
    public GoodsParticipantState {
        participants = Map.copyOf(participants);
        if (participants.size() > MAX_PARTICIPANTS || participants.entrySet().stream()
                .anyMatch(e -> !e.getKey().equals(e.getValue().party().id())))
            throw new IllegalArgumentException("invalid goods participant registry");
    }
    public static GoodsParticipantState empty() { return new GoodsParticipantState(Map.of()); }
    public GoodsParticipantState register(GoodsParticipant participant) {
        if (participants.containsKey(participant.party().id())) throw new IllegalArgumentException("duplicate goods participant");
        var next = new HashMap<>(participants); next.put(participant.party().id(), participant);
        return new GoodsParticipantState(next);
    }
    public GoodsParticipantState reviewed(SubjectId id, long revision, String decision, long tick) {
        var current = participants.get(id);
        if (current == null || current.reviewRevision() != revision) throw new IllegalArgumentException("stale participant review");
        var next = new HashMap<>(participants); next.put(id, current.reviewed(decision, tick));
        return new GoodsParticipantState(next);
    }
}
