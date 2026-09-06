package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;
import java.util.Optional;

/** Visible state of one engine lane. Quarantine never silently resumes. */
public record EngineStatus(Kind kind, String detail) {
    public enum Kind { ACTIVE, QUARANTINED }

    public EngineStatus {
        Objects.requireNonNull(kind, "engine status");
        if (detail != null && detail.length() > 240) {
            throw new IllegalArgumentException("engine status detail exceeds 240 characters");
        }
        if (kind == Kind.ACTIVE && detail != null) {
            throw new IllegalArgumentException("active engine cannot have failure detail");
        }
        if (kind == Kind.QUARANTINED && (detail == null || detail.isBlank())) {
            throw new IllegalArgumentException("quarantined engine requires detail");
        }
    }

    public static EngineStatus active() {
        return new EngineStatus(Kind.ACTIVE, null);
    }

    public Optional<String> failureDetail() {
        return Optional.ofNullable(detail);
    }
}
