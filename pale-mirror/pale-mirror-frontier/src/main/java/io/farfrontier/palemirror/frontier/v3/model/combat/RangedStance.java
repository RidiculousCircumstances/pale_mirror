package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;
/** Fresh native sight/line-of-fire facts; canonical positioning never guesses visibility through blocks. */
public record RangedStance(ActorActuationId observer,CombatTarget target,SurfaceAnchor targetPosition,boolean visible,boolean clearShot,long atTick) {
    public RangedStance{Objects.requireNonNull(observer);Objects.requireNonNull(target);Objects.requireNonNull(targetPosition);if(atTick<0 || clearShot&&!visible)throw new IllegalArgumentException("invalid ranged sighting");}
    public boolean current(CombatOrder order,long tick,RangedRules rules){return observer.execution().equals(order.execution()) && target.equals(order.target())
            && targetPosition.equals(order.targetPosition()) && tick>=atTick && tick-atTick<=2L*rules.sensingTicks();}
}
