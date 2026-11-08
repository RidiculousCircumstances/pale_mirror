package io.farfrontier.palemirror.frontier.v3.model.seating;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;

/** The shared seat owner addresses an exact reservation, not whoever happens to be nearby. */
public sealed interface SeatEvent extends FrontierPayload {
    default SubjectId subject() {
        return switch (this) {
            case Reserved e -> e.use().execution().actorId();
            case Prepared e -> e.actor();
            case Checkpointed e -> e.actor();
            case ApplicationCheckpointed e -> e.actor();
            case Changed e -> e.actor();
            case Released e -> e.actor();
        };
    }
    record Reserved(SeatUse use) implements SeatEvent {
        public static final String TYPE = "frontier.seat_reserved";
        public String type() { return TYPE; }
    }
    record Prepared(SubjectId actor, long revision, ActorActuationId body, SeatUse.Action action, SeatUse.ExecutionMode mode) implements SeatEvent {
        public Prepared { Objects.requireNonNull(mode); Objects.requireNonNull(action); Objects.requireNonNull(body); }
        public static final String TYPE = "frontier.seat_prepared";
        public String type() { return TYPE; }
        public boolean requiresDurableBeforeEffect() { return true; }
    }
    record Checkpointed(SubjectId actor, long revision, ActorActuationId body, SeatUse.Action action,
            SeatUse.ExecutionMode mode, BodyPosition observed) implements SeatEvent {
        public static final String TYPE = "frontier.seat_checkpointed";
        public String type() { return TYPE; }
        public boolean requiresDurableBeforeEffect() { return true; }
        public Checkpointed { Objects.requireNonNull(body); Objects.requireNonNull(action); Objects.requireNonNull(mode); Objects.requireNonNull(observed); }
    }
    /** Historical body observed at native application, backed by all exact saved native participants.
     * It settles an operation; it never installs this body as the current physical pose. */
    record ApplicationCheckpointed(SubjectId actor, long revision, ActorActuationId body, SeatUse.Action action,
            SeatUse.ExecutionMode mode, UUID operation, BodyPosition observed) implements SeatEvent {
        public static final String TYPE = "frontier.seat_application_checkpointed";
        public String type() { return TYPE; }
        public boolean requiresDurableBeforeEffect() { return true; }
        public ApplicationCheckpointed {
            Objects.requireNonNull(actor); Objects.requireNonNull(body); Objects.requireNonNull(action);
            Objects.requireNonNull(mode); Objects.requireNonNull(operation); Objects.requireNonNull(observed);
            if (revision < 1 || !actor.equals(body.body().actorId()))
                throw new IllegalArgumentException("seat application observation has a foreign exact body");
        }
    }
    record Changed(SubjectId actor, long revision, Optional<ActorActuationId> body, SeatUse.Action action, SeatUse.ExecutionMode mode,
            boolean applied, BodyPosition observed) implements SeatEvent {
        public static final String TYPE = "frontier.seat_changed";
        public String type() { return TYPE; }
        public boolean requiresDurableBeforeEffect() { return true; }
        public Changed {
            Objects.requireNonNull(body); Objects.requireNonNull(action); Objects.requireNonNull(mode); Objects.requireNonNull(observed);
            if (mode == SeatUse.ExecutionMode.COMPOSED_ACTIVITY_EFFECT && (body.isEmpty() || action != SeatUse.Action.EXIT))
                throw new IllegalArgumentException("composed seat receipt lacks its declared HOT exit tuple");
        }
    }
    record Released(SubjectId actor, long revision) implements SeatEvent {
        public static final String TYPE = "frontier.seat_released";
        public String type() { return TYPE; }
    }
}
