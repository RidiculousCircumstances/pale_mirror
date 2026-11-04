package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.Optional;
/** Personal preference over legal approach candidates. Contact, sector and geometry remain hard constraints. */
public final class MeleeRearApproach {
    public static Optional<CombatFacing> facing(CombatState combat,CombatOrder order,long tick,MeleeRules rules){
        var stance=combat.stances().get(order.owner());
        return stance!=null && stance.current(order,tick,rules) && !stance.attackReady()?stance.targetFacing():Optional.empty();
    }
    public static double cost(CombatOrder order,SurfaceAnchor station,Optional<CombatFacing> facing){
        return facing.map(value->order.style().rearWeight()/100.0*(1+value.alignment(station,order.targetPosition()))).orElse(0.0);
    }
    private MeleeRearApproach(){}
}
