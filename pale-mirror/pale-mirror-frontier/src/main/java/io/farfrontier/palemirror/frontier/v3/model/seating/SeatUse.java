package io.farfrontier.palemirror.frontier.v3.model.seating;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import java.util.*;

/** One activity's finite seat reservation and durable local pose interaction. Independent of a retained bed. */
public record SeatUse(ActorExecutionId execution, SeatPlace place, Phase phase, long revision, long changedAt, long carrierEpoch,
        Optional<ActorActuationId> pendingBody, Optional<Action> pendingAction, Optional<ExecutionMode> pendingMode,
        Optional<BodyPosition> pendingObservation) {
    public enum ExecutionMode {
        LOCAL(1), COMPOSED_ACTIVITY_EFFECT(2);
        private final int tag; ExecutionMode(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static ExecutionMode fromWireTag(int tag) {
            for (var value : values()) if (value.tag == tag) return value;
            throw new IllegalArgumentException("unknown declared seat execution mode");
        }
    }
    public enum Phase {
        RESERVED(1), SEATED(2);
        private final int tag; Phase(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Phase fromWireTag(int tag) {
            for (var value : values()) if (value.tag == tag) return value;
            throw new IllegalArgumentException("unknown seat phase");
        }
    }
    public enum Action {
        ENTER(1), EXIT(2), RESTORE(3);
        private final int tag; Action(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Action fromWireTag(int tag) {
            for (var value : values()) if (value.tag == tag) return value;
            throw new IllegalArgumentException("unknown seat action");
        }
    }
    public SeatUse {
        Objects.requireNonNull(execution); Objects.requireNonNull(place); Objects.requireNonNull(phase);
        Objects.requireNonNull(pendingBody); Objects.requireNonNull(pendingAction); Objects.requireNonNull(pendingMode);
        Objects.requireNonNull(pendingObservation);
        if (revision < 1 || changedAt < 0 || carrierEpoch < 1 || pendingBody.isPresent() != pendingAction.isPresent()
                || pendingBody.isPresent() != pendingMode.isPresent()
                || pendingObservation.isPresent() && pendingBody.isEmpty()
                || pendingMode.equals(Optional.of(ExecutionMode.COMPOSED_ACTIVITY_EFFECT)) && !pendingAction.equals(Optional.of(Action.EXIT))
                || pendingBody.filter(body -> !body.execution().equals(execution)).isPresent()
                || pendingAction.filter(action -> phase == Phase.RESERVED ? action != Action.ENTER : action == Action.ENTER).isPresent())
            throw new IllegalArgumentException("invalid seat reservation/interaction");
    }
    public static SeatUse reserve(ActorExecutionId execution, SeatPlace place, long tick) {
        return new SeatUse(execution, place, Phase.RESERVED, 1, tick, 1, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
    public SeatUse prepared(ActorActuationId body, Action action, ExecutionMode mode) {
        if (pendingBody.isPresent()) throw new IllegalArgumentException("seat interaction already prepared");
        return new SeatUse(execution, place, phase, revision, changedAt, carrierEpoch, Optional.of(body), Optional.of(action), Optional.of(mode), Optional.empty());
    }
    public SeatUse checkpointed(BodyPosition observation) {
        if (pendingBody.isEmpty() || pendingObservation.filter(old -> !old.equals(observation)).isPresent())
            throw new IllegalArgumentException("seat operation cannot replace its observed effect checkpoint");
        return new SeatUse(execution, place, phase, revision, changedAt, carrierEpoch, pendingBody, pendingAction, pendingMode, Optional.of(observation));
    }
    public SeatUse changed(Action action, boolean applied, long tick) {
        var next = applied ? (action == Action.EXIT ? Phase.RESERVED : Phase.SEATED) : phase;
        return new SeatUse(execution, place, next, Math.addExact(revision, 1), next == phase ? changedAt : tick,
                applied && action == Action.EXIT ? Math.addExact(carrierEpoch, 1) : carrierEpoch,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
    public SeatUse reselected(SeatPlace replacement, long tick) {
        if (phase != Phase.RESERVED || pendingBody.isPresent() || tick < changedAt
                || !place.home().equals(replacement.home()) || place.id().equals(replacement.id()))
            throw new IllegalArgumentException("only an unused settled seat can change");
        return new SeatUse(execution, replacement, phase, Math.addExact(revision, 1), tick,
                Math.addExact(carrierEpoch, 1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
    public SeatUse resumed(ActorExecutionId successor) {
        if (pendingBody.isPresent() || phase != Phase.RESERVED
                || !execution.actorId().equals(successor.actorId()) || !execution.activityOwnerId().equals(successor.activityOwnerId())
                || execution.activityKind() != successor.activityKind())
            throw new IllegalArgumentException("seat continuation lacks a standing exact predecessor");
        return new SeatUse(successor, place, phase, revision + 1, changedAt, carrierEpoch, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
