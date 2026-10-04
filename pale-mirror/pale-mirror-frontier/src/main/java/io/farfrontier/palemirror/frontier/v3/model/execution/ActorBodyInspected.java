package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import java.util.Objects;
import java.util.Optional;

/** Common physical observation, not admission, activity arrival or cargo recovery. */
public record ActorBodyInspected(ActorBodyId body, Source source, BodyPosition expectedBody, FixedScalar expectedHealth,
                                 BodyPosition observedBody, FixedScalar observedHealth,
                                 Optional<ActorExecutionId> expectedExecution) implements FrontierPayload {
    /** Saved evidence records pose only; it cannot grant a loaded actuator or resolve its ambiguity. */
    public enum Source {
        INDEXED_LIVING(1), SAVED_DEPARTURE(2);
        private final int wireTag;
        Source(int wireTag) { this.wireTag = wireTag; }
        public int wireTag() { return wireTag; }
        public static Source fromWireTag(int tag) {
            return switch (tag) {
                case 1 -> INDEXED_LIVING;
                case 2 -> SAVED_DEPARTURE;
                default -> throw new IllegalArgumentException("unknown body inspection source");
            };
        }
    }
    public ActorBodyInspected {
        Objects.requireNonNull(body); Objects.requireNonNull(source); Objects.requireNonNull(expectedBody); Objects.requireNonNull(expectedHealth);
        Objects.requireNonNull(observedBody); Objects.requireNonNull(observedHealth); Objects.requireNonNull(expectedExecution);
        if (expectedHealth.compareTo(FixedScalar.ZERO) <= 0 || observedHealth.compareTo(FixedScalar.ZERO) <= 0
                || expectedExecution.filter(id -> !id.actorId().equals(body.actorId())).isPresent())
            throw new IllegalArgumentException("body inspection requires one exact living actor");
    }
    @Override public String type() { return "frontier.actor_body_inspected"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
