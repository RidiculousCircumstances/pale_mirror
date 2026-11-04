package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.Objects;
import java.util.Optional;
/** Short-lived native opportunity. Never awards semantic movement arrival. */
public record MeleeStance(ActorActuationId observer,CombatTarget target,boolean holding,boolean attackReady,long atTick,Optional<CombatFacing> targetFacing) {
    public MeleeStance {Objects.requireNonNull(observer);Objects.requireNonNull(target);Objects.requireNonNull(targetFacing);if(atTick<0)throw new IllegalArgumentException("invalid stance time");}
    public boolean current(CombatOrder order,long tick,MeleeRules rules){return observer.execution().equals(order.execution())
            && target.equals(order.target()) && tick>=atTick && tick-atTick<=3L*rules.sensingInterval();}
}
