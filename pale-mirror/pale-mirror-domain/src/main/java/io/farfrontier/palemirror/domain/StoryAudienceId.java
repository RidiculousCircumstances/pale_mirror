package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Persistent identity for an audience, independent of a mutable scoreboard-team name. */
public record StoryAudienceId(String value) {
    public StoryAudienceId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Story audience id cannot be blank");
        }
    }

    public static StoryAudienceId globalTestAudience() {
        return new StoryAudienceId("pm:default-audience");
    }
}
