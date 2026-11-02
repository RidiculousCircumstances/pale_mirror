package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import java.util.*;
/** One bounded safety episode per actor, including its request before an owner can yield. */
public record Evasion(SubjectId id,SubjectId actor,SubjectId scope,CombatTarget threat,
        Optional<SurfaceAnchor> threatPosition,SurfaceAnchor incidentPosition,long observedAt,long quietUntil,
        long revision,Optional<ActorExecutionId> execution,Optional<SurfaceAnchor> station) {
    public Evasion {
        Objects.requireNonNull(id);Objects.requireNonNull(actor);Objects.requireNonNull(scope);Objects.requireNonNull(threat);
        Objects.requireNonNull(threatPosition);Objects.requireNonNull(incidentPosition);Objects.requireNonNull(execution);Objects.requireNonNull(station);
        if(observedAt<0 || quietUntil<0 || revision<1 || station.isPresent() && execution.isEmpty()
                || execution.filter(e->!e.actorId().equals(actor)||!e.activityOwnerId().equals(id)||e.activityKind()!=ActorActivityKind.SAFETY).isPresent())
            throw new IllegalArgumentException("invalid safety episode declaration");
    }
    public MovementOrder order(){return new MovementOrder(id,actor,1,revision,List.of(station.orElseThrow()),
            TraversalCapability.PEDESTRIAN,MovementOrder.ArrivalPolicy.EXACT_STATION);}
    public Evasion started(ActorExecutionId value){return new Evasion(id,actor,scope,threat,threatPosition,incidentPosition,observedAt,quietUntil,revision,Optional.of(value),station);}
    public Evasion destination(SurfaceAnchor value){return new Evasion(id,actor,scope,threat,threatPosition,incidentPosition,observedAt,quietUntil,Math.incrementExact(revision),execution,Optional.of(value));}
    public Evasion observed(CombatTarget target,Optional<SurfaceAnchor> point,SurfaceAnchor incident,long tick,EvasionRules rules,boolean immediate){
        return new Evasion(id,actor,scope,target,point,incident,tick,immediate?Math.addExact(tick,rules.quietTicks()):quietUntil,revision,execution,station);
    }
}
