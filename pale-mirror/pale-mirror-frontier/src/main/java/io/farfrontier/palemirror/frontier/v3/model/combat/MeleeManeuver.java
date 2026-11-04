package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;
/** One exact fixed destination and bounded lifetime; retained terminal entry owns both cooldowns. */
public record MeleeManeuver(ActorActuationId actor,SubjectId weapon,CombatTarget target,Kind kind,long attemptOrdinal,
        SurfaceAnchor origin,SurfaceAnchor station,long selectedAt,long launchedAt,long closedAt,
        long burstReadyAt,long retreatReadyAt,long anyReadyAt,long runReadyAt) {
    public enum Kind {
        BURST(1), BACKSTEP(2), SIDESTEP_LEFT(3), SIDESTEP_RIGHT(4), PURSUIT_RUN(5), DIAGONAL_LEFT(6), DIAGONAL_RIGHT(7), ADVANCE_LEFT(8), ADVANCE_RIGHT(9), BLOCK(10);
        private final int tag;Kind(int tag){this.tag=tag;}public int wireTag(){return tag;}
        public boolean lateral(){return this==SIDESTEP_LEFT || this==SIDESTEP_RIGHT || this==DIAGONAL_LEFT || this==DIAGONAL_RIGHT || this==ADVANCE_LEFT || this==ADVANCE_RIGHT;}
        public boolean closing(){return this==BURST || this==PURSUIT_RUN;}
        public int side(){return this==SIDESTEP_LEFT || this==DIAGONAL_LEFT || this==ADVANCE_LEFT?1:this==SIDESTEP_RIGHT || this==DIAGONAL_RIGHT || this==ADVANCE_RIGHT?-1:0;}
        public Kind opposite(){return switch(this){case SIDESTEP_LEFT->SIDESTEP_RIGHT;case SIDESTEP_RIGHT->SIDESTEP_LEFT;
            case DIAGONAL_LEFT->DIAGONAL_RIGHT;case DIAGONAL_RIGHT->DIAGONAL_LEFT;case ADVANCE_LEFT->ADVANCE_RIGHT;case ADVANCE_RIGHT->ADVANCE_LEFT;
            default->throw new IllegalArgumentException("maneuver has no lateral opposite");};}
        public static Kind require(int tag){for(var k:values())if(k.tag==tag)return k;throw new IllegalArgumentException("unknown melee maneuver");}
    }
    public MeleeManeuver {
        Objects.requireNonNull(actor);Objects.requireNonNull(weapon);Objects.requireNonNull(target);Objects.requireNonNull(kind);Objects.requireNonNull(origin);Objects.requireNonNull(station);
        if(attemptOrdinal<1 || selectedAt<0 || launchedAt<0 || closedAt<0 || launchedAt>0 && launchedAt<selectedAt
                || closedAt>0 && (closedAt<selectedAt || launchedAt>closedAt) || origin.equals(station) && kind!=Kind.BLOCK
                || origin.manhattanDistance(station)>(kind==Kind.PURSUIT_RUN?16:6)
                || burstReadyAt<0 || retreatReadyAt<0 || runReadyAt<0 || anyReadyAt<selectedAt)throw new IllegalArgumentException("invalid melee maneuver");
    }
    public SubjectId owner(){return actor.execution().activityOwnerId();}
    public boolean open(){return closedAt==0;}
    /** Closed attempts are bounded history/cooldown, never a successor's actuation permission. */
    public boolean retainedBy(io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution,SubjectId currentWeapon){
        var original=actor.execution();
        return original.activityKind()==execution.activityKind() && original.activityOwnerId().equals(execution.activityOwnerId())
            && original.actorId().equals(execution.actorId()) && original.generation()<=execution.generation()
            && (!open() || original.equals(execution) && weapon.equals(currentWeapon));
    }
    public boolean matches(CombatOrder order){return actor.execution().equals(order.execution()) && weapon.equals(order.weapon()) && target.equals(order.target());}
    public boolean busy(long tick,ManeuverRules rules){return open() || tick<closedAt+rules.recovery(kind);}
    /** A completed closing burst hands contact to the weapon; evasive recovery remains committed. */
    public boolean permitsContactTransition(CombatProfile profile){
        return kind.closing() || kind!=Kind.BLOCK && profile.knows(CombatSkillId.EVASION,CombatTrainingCatalog.standard().number("defence.counter"));
    }
    public boolean blocksStrike(long tick,ManeuverRules rules){return open() || !kind.closing() && tick<closedAt+rules.recovery(kind);}
    public boolean ready(Kind next,long tick){return !open() && tick>=anyReadyAt && tick>=switch(next){
        case BURST->burstReadyAt;case PURSUIT_RUN->runReadyAt;case BACKSTEP,SIDESTEP_LEFT,SIDESTEP_RIGHT,DIAGONAL_LEFT,DIAGONAL_RIGHT,ADVANCE_LEFT,ADVANCE_RIGHT,BLOCK->retreatReadyAt;};}
    public MeleeManeuver launched(long tick){if(!open() || launchedAt!=0 || tick<selectedAt)throw new IllegalArgumentException("stale maneuver launch");return copy(tick,0);}
    public MeleeManeuver closed(long tick){if(!open() || tick<selectedAt)throw new IllegalArgumentException("stale maneuver closure");return copy(launchedAt,tick);}
    private MeleeManeuver copy(long launch,long close){return new MeleeManeuver(actor,weapon,target,kind,attemptOrdinal,origin,station,selectedAt,launch,close,burstReadyAt,retreatReadyAt,anyReadyAt,runReadyAt);}
    public MovementOrder order(){return new MovementOrder(owner(),actor.execution().actorId(),attemptOrdinal,1,List.of(station),TraversalCapability.PEDESTRIAN,MovementOrder.ArrivalPolicy.EXACT_STATION);}
}
