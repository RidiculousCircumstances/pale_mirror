package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One concise, object-local player briefing derived from canonical world state. */
public record FrontierObjectBoard(SubjectId ownerId, BlockPosition position, Tone tone, Scope scope, String text) {
    public enum Tone { SETTLEMENT, HIVE, WARNING }
    /**
     * A semantic distance class, not a client preference or a second state store.
     * Landmark boards orient approach to a settlement or nest. Local boards explain only the
     * object a player is already approaching, so a whole settlement never becomes a wall of text.
     */
    public enum Scope { LANDMARK, LOCAL }

    public FrontierObjectBoard {
        Objects.requireNonNull(ownerId, "board owner id");
        Objects.requireNonNull(position, "board position");
        Objects.requireNonNull(tone, "board tone");
        Objects.requireNonNull(scope, "board scope");
        if (text == null || text.isBlank() || text.length() > 120) throw new IllegalArgumentException("board text must be 1..120 characters");
    }

    /** Existing callers which do not identify a world landmark remain intentionally local. */
    public FrontierObjectBoard(SubjectId ownerId, BlockPosition position, Tone tone, String text) {
        this(ownerId, position, tone, Scope.LOCAL, text);
    }
}
