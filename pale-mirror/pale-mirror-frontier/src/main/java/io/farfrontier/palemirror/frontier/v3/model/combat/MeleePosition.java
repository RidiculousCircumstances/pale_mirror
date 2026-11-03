package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.*;
/** Temporary spatial claim or explicit wait. Not another movement goal/controller. */
public record MeleePosition(ActorExecutionId execution,CombatTarget target,SurfaceAnchor targetPosition,
        Optional<SurfaceAnchor> station,int widthMicros,int heightMicros,Reason reason,long sinceTick,long atTick,long retryAt) {
    public enum Reason {
        RESERVED(1),ROUTE_PENDING(2),SPACE_OCCUPIED(3),NO_PATH(4),NO_POSITION(5),UNREACHABLE(6);
        private final int tag;Reason(int tag){this.tag=tag;}public int wireTag(){return tag;}
        public static Reason require(int tag){for(var value:values())if(value.tag==tag)return value;throw new IllegalArgumentException("unknown melee position reason");}
        public boolean unavailable(){return this==NO_PATH || this==NO_POSITION;}
    }
    public MeleePosition renewed(long tick){return new MeleePosition(execution,target,targetPosition,station,widthMicros,heightMicros,reason,sinceTick,tick,retryAt);}
    public MeleePosition {
        Objects.requireNonNull(execution);Objects.requireNonNull(target);Objects.requireNonNull(targetPosition);station=Objects.requireNonNull(station);Objects.requireNonNull(reason);
        if(widthMicros<100_000 || widthMicros>4_000_000 || heightMicros<100_000 || heightMicros>8_000_000 || sinceTick<0 || atTick<sinceTick
                || (reason==Reason.RESERVED)!=station.isPresent() || (reason==Reason.UNREACHABLE?retryAt<=atTick:retryAt!=0))
            throw new IllegalArgumentException("invalid melee spatial claim/wait");
    }
    public boolean sameOpponent(CombatOrder order){return execution.equals(order.execution()) && target.equals(order.target());}
    public boolean matches(CombatOrder order){return execution.equals(order.execution()) && target.equals(order.target()) && targetPosition.equals(order.targetPosition());}
    public boolean active(long tick,MeleeRules rules){return reason==Reason.RESERVED && tick>=atTick && tick-atTick<=rules.positionLeaseTicks();}
    public boolean deferred(CombatOrder order,long tick){return matches(order) && reason==Reason.UNREACHABLE && tick<retryAt;}
    public boolean overlaps(SurfaceAnchor other,int width,int height){
        if(station.isEmpty())return false;var point=station.orElseThrow();
        double half=(widthMicros+(double)width)/2_000_000.0;
        return Math.abs((long)point.x()-other.x())<half && Math.abs((long)point.z()-other.z())<half
                && point.y()<other.y()+height/1_000_000.0 && other.y()<point.y()+heightMicros/1_000_000.0;
    }
}
