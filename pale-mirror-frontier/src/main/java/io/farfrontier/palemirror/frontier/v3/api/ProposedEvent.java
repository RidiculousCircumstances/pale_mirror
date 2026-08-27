package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;

/** An event proposal that has not yet passed the engine's atomic commit boundary. */
public record ProposedEvent(SubjectId subject, FrontierPayload payload) {
    public ProposedEvent {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(payload, "payload");
    }
}
