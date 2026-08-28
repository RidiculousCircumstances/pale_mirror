package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.Objects;
import java.util.Optional;

/** Read-only lifecycle state for diagnostics; a quarantined host never resumes implicitly. */
record FrontierV3RuntimeStatus(Kind kind, Optional<String> detail) {
    enum Kind { ACTIVE, QUARANTINED, STOPPED }

    FrontierV3RuntimeStatus {
        Objects.requireNonNull(kind, "kind");
        detail = Objects.requireNonNull(detail, "detail");
        if (kind == Kind.ACTIVE && detail.isPresent()) throw new IllegalArgumentException("active runtime cannot have failure detail");
        if (kind == Kind.QUARANTINED && detail.isEmpty()) throw new IllegalArgumentException("quarantined runtime needs failure detail");
    }

    static FrontierV3RuntimeStatus active() { return new FrontierV3RuntimeStatus(Kind.ACTIVE, Optional.empty()); }
    static FrontierV3RuntimeStatus stopped() { return new FrontierV3RuntimeStatus(Kind.STOPPED, Optional.empty()); }
    static FrontierV3RuntimeStatus quarantined(RuntimeException error) {
        String detail = error.getClass().getSimpleName() + ": " + Objects.toString(error.getMessage(), "no detail");
        return new FrontierV3RuntimeStatus(Kind.QUARANTINED, Optional.of(detail.length() <= 240 ? detail : detail.substring(0, 240)));
    }
}
