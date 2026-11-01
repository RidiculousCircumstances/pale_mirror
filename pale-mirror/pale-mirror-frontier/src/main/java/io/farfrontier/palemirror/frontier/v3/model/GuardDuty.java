package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import java.util.*;

/** One retained peaceful appointment. Equipment custody and current activity are separate owners. */
public record GuardDuty(SubjectId id, SubjectId home, ActorExecutionId execution, SubjectId weapon,
        ShipmentEndpoint source, Phase phase, long revision, Optional<io.farfrontier.palemirror.frontier.v3.model.security.GuardMission> mission) {
    public GuardDuty(SubjectId id, SubjectId home, ActorExecutionId execution, SubjectId weapon, ShipmentEndpoint source, Phase phase, long revision) {
        this(id, home, execution, weapon, source, phase, revision, Optional.empty());
    }
    public GuardDuty withMission(io.farfrontier.palemirror.frontier.v3.model.security.GuardMission next) {
        return new GuardDuty(id, home, execution, weapon, source, phase, Math.incrementExact(revision), Optional.of(next));
    }
    public MovementOrder missionOrder() {
        var goal = mission.orElseThrow();
        if (phase != Phase.ON_DUTY) throw new IllegalArgumentException("equipment appointment has no patrol authority");
        return new MovementOrder(id, execution.actorId(), 20, revision, List.of(goal.station()), TraversalCapability.PEDESTRIAN,
                MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
    public enum Phase {
        EQUIPPING(1), ON_DUTY(2), RETURNING(3);
        private final int tag;
        Phase(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Phase require(int tag) {
            for (var value : values()) if (value.tag == tag) return value;
            throw new IllegalArgumentException("unknown guard duty phase");
        }
    }
    public GuardDuty {
        Objects.requireNonNull(id); Objects.requireNonNull(home); Objects.requireNonNull(execution);
        mission = Objects.requireNonNull(mission);
        Objects.requireNonNull(weapon); Objects.requireNonNull(source); Objects.requireNonNull(phase);
        if (revision < 1 || execution.activityKind() != ActorActivityKind.GUARD
                || !execution.activityOwnerId().equals(id) || !source.settlementId().equals(home))
            throw new IllegalArgumentException("guard duty lacks its complete appointment/execution/source declaration");
    }
    public MovementOrder order() {
        if (phase == Phase.ON_DUTY) throw new IllegalArgumentException("standing guard has no equipment approach");
        return new MovementOrder(id, execution.actorId(), phase.wireTag(), revision, List.of(source.station()),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
    public GuardDuty equipped() {
        return new GuardDuty(id, home, execution, weapon, source, Phase.ON_DUTY, Math.incrementExact(revision));
    }
    public GuardDuty acquiring(SubjectId item, ShipmentEndpoint endpoint) {
        return new GuardDuty(id, home, execution, item, endpoint, Phase.EQUIPPING, Math.incrementExact(revision));
    }
    public GuardDuty returning(ShipmentEndpoint destination) {
        return new GuardDuty(id, home, execution, weapon, destination, Phase.RETURNING, Math.incrementExact(revision));
    }
    public GuardDuty resumed(ActorExecutionId successor) {
        if (!successor.actorId().equals(execution.actorId()) || !successor.activityOwnerId().equals(id)
                || successor.activityKind() != execution.activityKind() || successor.generation() <= execution.generation())
            throw new IllegalArgumentException("foreign guard resumption");
        return new GuardDuty(id, home, successor, weapon, source, phase, Math.incrementExact(revision), mission);
    }
}
