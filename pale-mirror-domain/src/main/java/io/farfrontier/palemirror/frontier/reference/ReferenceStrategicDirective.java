package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/** AI directive ported from Python {@code StrategicDirective}. */
public record ReferenceStrategicDirective(
        int id,
        ReferenceAgentRef recipient,
        ReferenceDirectiveKind kind,
        ReferenceTargetRef target,
        double weight,
        int issuedDay,
        int expiresDay
) {
    public ReferenceStrategicDirective {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(kind, "kind");
    }

    public boolean activeOn(int day) { return day <= expiresDay; }
}
