package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;
/** Native facts and exact hit lifecycle. No role receives its own parallel melee protocol. */
public sealed interface CombatEvent extends FrontierPayload permits CombatEvent.Prepared,CombatEvent.Completed,CombatEvent.WeaponObserved,CombatEvent.PositionBlocked,CombatEvent.WeaponDefined,CombatEvent.StanceObserved,
        CombatEvent.PositionPlanned,CombatEvent.ManeuverRequested,CombatEvent.ManeuverSelected,CombatEvent.ManeuverLaunched,CombatEvent.ManeuverClosed {
    record ManeuverRequested(ActorActuationId observer,CombatTarget target,SurfaceAnchor origin,MeleeManeuvers.Facts facts) implements CombatEvent {
        public static final String TYPE="frontier.combat.maneuver_requested";
        public ManeuverRequested{Objects.requireNonNull(observer);Objects.requireNonNull(target);Objects.requireNonNull(origin);Objects.requireNonNull(facts);}public String type(){return TYPE;}
    }
    record ManeuverSelected(MeleeManeuver maneuver) implements CombatEvent {
        public static final String TYPE="frontier.combat.maneuver_selected";
        public ManeuverSelected{Objects.requireNonNull(maneuver);}public String type(){return TYPE;}
    }
    record ManeuverLaunched(ActorActuationId observer,long attemptOrdinal) implements CombatEvent {
        public static final String TYPE="frontier.combat.maneuver_launched";
        public ManeuverLaunched{Objects.requireNonNull(observer);if(attemptOrdinal<1)throw new IllegalArgumentException("invalid maneuver launch");}public String type(){return TYPE;}
    }
    record ManeuverClosed(SubjectId owner,long attemptOrdinal) implements CombatEvent {
        public static final String TYPE="frontier.combat.maneuver_closed";
        public ManeuverClosed{Objects.requireNonNull(owner);if(attemptOrdinal<1)throw new IllegalArgumentException("invalid maneuver closure");}public String type(){return TYPE;}
    }
    record StanceObserved(MeleeStance stance) implements CombatEvent {
        public static final String TYPE="frontier.combat.stance_observed";
        public StanceObserved{Objects.requireNonNull(stance);}public String type(){return TYPE;}
    }
    record PositionPlanned(io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution,CombatTarget target,
            SurfaceAnchor targetPosition,Optional<SurfaceAnchor> station,MeleePosition.Reason reason) implements CombatEvent {
        public static final String TYPE="frontier.combat.position_planned";
        public PositionPlanned{Objects.requireNonNull(execution);Objects.requireNonNull(target);Objects.requireNonNull(targetPosition);Objects.requireNonNull(station);Objects.requireNonNull(reason);
            if((reason==MeleePosition.Reason.RESERVED)!=station.isPresent() || reason==MeleePosition.Reason.UNREACHABLE)throw new IllegalArgumentException("invalid position proposal");}
        public String type(){return TYPE;}
    }
    record Prepared(MeleeStrike strike) implements CombatEvent {
        public static final String TYPE="frontier.combat.prepared";
        public Prepared {Objects.requireNonNull(strike);} public String type(){return TYPE;}
        public boolean requiresDurableBeforeEffect(){return true;}
    }
    record Completed(SubjectId owner,long attemptOrdinal,boolean applied,ItemCondition after) implements CombatEvent {
        public static final String TYPE="frontier.combat.completed";
        public Completed {Objects.requireNonNull(owner);Objects.requireNonNull(after);if(attemptOrdinal<1)throw new IllegalArgumentException("invalid melee outcome");}
        public String type(){return TYPE;} public boolean requiresDurableBeforeEffect(){return true;}
    }
    record WeaponObserved(ActorActuationId observer,SubjectId weapon,WeaponProfile profile) implements CombatEvent {
        public static final String TYPE="frontier.combat.weapon_observed";
        public WeaponObserved {Objects.requireNonNull(observer);Objects.requireNonNull(weapon);Objects.requireNonNull(profile);}
        public String type(){return TYPE;}
    }
    /** Registry definition is useful for COLD approaches, never physical hit authority. */
    record WeaponDefined(io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution,SubjectId weapon,WeaponProfile profile) implements CombatEvent {
        public static final String TYPE="frontier.combat.weapon_defined";
        public WeaponDefined {Objects.requireNonNull(execution);Objects.requireNonNull(weapon);Objects.requireNonNull(profile);}
        public String type(){return TYPE;}
    }
    record PositionBlocked(ActorActuationId observer,CombatTarget target,SurfaceAnchor station) implements CombatEvent {
        public static final String TYPE="frontier.combat.position_blocked";
        public PositionBlocked {Objects.requireNonNull(observer);Objects.requireNonNull(target);Objects.requireNonNull(station);}
        public String type(){return TYPE;}
    }
}
