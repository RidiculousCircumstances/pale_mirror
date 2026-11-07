package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.Objects;

/** Last confirmed launch, scoped to one encounter. A cancelled preparation creates no tactical credit. */
public record RangedFollowup(ActorExecutionId execution,SubjectId weapon,CombatTarget target,
        SurfaceAnchor origin,RangedTechnique technique,long atTick){
    public RangedFollowup {
        Objects.requireNonNull(execution);Objects.requireNonNull(weapon);Objects.requireNonNull(target);Objects.requireNonNull(origin);Objects.requireNonNull(technique);
        if(atTick<0)throw new IllegalArgumentException("invalid ranged followup");
    }
    public boolean current(CombatOrder order,long tick){return execution.equals(order.execution())&&weapon.equals(order.weapon())&&target.equals(order.target())&&tick>=atTick&&tick-atTick<=80;}
    public boolean moved(SurfaceAnchor actual){return RangedPositions.distance(origin,actual)>=3;}
}
