package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;

/** Bounded diagnostic for a rejected command. */
public record CommandRejection(RejectionCode code, String detail) {
    private static final int MAX_DETAIL_LENGTH = 240;

    public CommandRejection {
        Objects.requireNonNull(code, "rejection code");
        Objects.requireNonNull(detail, "rejection detail");
        if (detail.isBlank() || detail.length() > MAX_DETAIL_LENGTH) {
            throw new IllegalArgumentException("rejection detail must contain 1-" + MAX_DETAIL_LENGTH + " characters");
        }
    }
}
