package io.farfrontier.palemirror.frontier.v3.model.rest;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;

/** Exact long-lived place reservation; it never holds a doctor or an inventory service turn. */
public record RestUse(ActorExecutionId execution,RestPlace place,SurfaceAnchor approach,Phase phase,long revision,long changedAt,
        Optional<ActorActuationId> pendingBody,Optional<Action> pendingAction,Optional<Interval> completed){
    public record Interval(long start,long end){public Interval{if(start<0||end<start)throw new IllegalArgumentException("invalid rest interval");}}
    public RestUse(ActorExecutionId execution,RestPlace place,SurfaceAnchor approach,Phase phase,long revision,long changedAt,
            Optional<ActorActuationId> pendingBody,Optional<Action> pendingAction){this(execution,place,approach,phase,revision,changedAt,pendingBody,pendingAction,Optional.empty());}
    public enum Phase { RESERVED(1), RESTING(2); private final int tag; Phase(int tag){this.tag=tag;} public int wireTag(){return tag;} public static Phase fromWireTag(int tag){for(var v:values())if(v.tag==tag)return v;throw new
        IllegalArgumentException("unknown rest phase");} }
    public enum Action { ENTER(1), EXIT(2), RESTORE(3); private final int tag; Action(int tag){this.tag=tag;} public int wireTag(){return tag;} public static Action fromWireTag(int tag){for(var v:values())if(v.tag==tag)return v;
        throw new IllegalArgumentException("unknown rest action");} }
    public RestUse {
        Objects.requireNonNull(execution);Objects.requireNonNull(place);Objects.requireNonNull(approach);Objects.requireNonNull(phase);
        Objects.requireNonNull(pendingBody);Objects.requireNonNull(pendingAction);Objects.requireNonNull(completed);
        if(revision<1||changedAt<0||pendingBody.isPresent()!=pendingAction.isPresent()
                ||pendingAction.filter(a->phase==Phase.RESERVED?a!=Action.ENTER:a==Action.ENTER).isPresent()
                ||phase==Phase.RESTING&&completed.isPresent()
                ||completed.filter(i->i.end()>changedAt).isPresent()||pendingBody.filter(a->!a.execution().equals(execution)).isPresent()
                ||Math.abs(approach.x()-place.head().x())+Math.abs(approach.z()-place.head().z())>3||Math.abs(approach.y()-place.head().y())>2)
            throw new IllegalArgumentException("invalid rest reservation/interaction");
    }
    public RestUse prepared(ActorActuationId body,Action action){
        if(pendingBody.isPresent()||(phase==Phase.RESERVED?action!=Action.ENTER:action==Action.ENTER))throw new IllegalArgumentException("rest interaction contradicts predecessor");
        return new RestUse(execution,place,approach,phase,revision,changedAt,Optional.of(body),Optional.of(action),completed);
    }
    public RestUse changed(Phase next,long tick){return changed(next,tick,approach);}
    public RestUse changed(Phase next,long tick,SurfaceAnchor nextApproach){return new RestUse(execution,place,nextApproach,next,revision+1,next==phase?changedAt:tick,Optional.empty(),Optional.empty(),
            phase==Phase.RESTING&&next==Phase.RESERVED?Optional.of(new Interval(changedAt,tick)):next==Phase.RESTING&&phase!=next?Optional.empty():completed);}
    public RestUse resumed(ActorExecutionId successor){
        if(pendingBody.isPresent()||!execution.actorId().equals(successor.actorId())||!execution.activityOwnerId().equals(successor.activityOwnerId()))
            throw new IllegalArgumentException("unsettled or foreign resting continuation");
        return new RestUse(successor,place,approach,phase,revision+1,changedAt,Optional.empty(),Optional.empty(),completed);
    }
}
