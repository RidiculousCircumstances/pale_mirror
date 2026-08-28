package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One concise, object-local player briefing derived from canonical world state. */
public record FrontierObjectBoard(SubjectId ownerId, BlockPosition position, Tone tone, String text) {
    public enum Tone { SETTLEMENT, HIVE, WARNING }

    public FrontierObjectBoard {
        Objects.requireNonNull(ownerId, "board owner id");
        Objects.requireNonNull(position, "board position");
        Objects.requireNonNull(tone, "board tone");
        if (text == null || text.isBlank() || text.length() > 120) throw new IllegalArgumentException("board text must be 1..120 characters");
    }
}
