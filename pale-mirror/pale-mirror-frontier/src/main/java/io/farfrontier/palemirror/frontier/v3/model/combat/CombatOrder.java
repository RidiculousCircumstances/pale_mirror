package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.*;
/** Immutable caller permission. Tactical code owns no appointment, political relation or body. */
public record CombatOrder(ActorExecutionId execution,long revision,SubjectId weapon,CombatEngagement engagement,
        SurfaceAnchor targetPosition,long observedAt,long lifetime,SurfaceAnchor actorPosition,io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile navigation,Optional<SurfaceAnchor> station) {
    public CombatOrder {
        Objects.requireNonNull(execution);Objects.requireNonNull(weapon);Objects.requireNonNull(engagement);
        Objects.requireNonNull(targetPosition);Objects.requireNonNull(actorPosition);Objects.requireNonNull(navigation);Objects.requireNonNull(station);
        if(revision<1 || observedAt<0 || lifetime<1 || !engagement.contains(targetPosition))throw new IllegalArgumentException("invalid combat permission");
    }
    /** Native planning input only; common body inspection alone publishes a position. */
    public CombatOrder atActorObservation(SurfaceAnchor observed){return new CombatOrder(execution,revision,weapon,engagement,targetPosition,observedAt,lifetime,observed,navigation,station);}
    public SubjectId owner(){return execution.activityOwnerId();}
    public CombatTarget target(){return engagement.target();}
    public boolean current(long tick){return tick>=observedAt && tick-observedAt<=lifetime;}
}
