package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
/** Safety episode lifecycle; current owners alone prove interruption and resources remain theirs. */
public final class EvasionTransitions {
    public static FrontierWorldState apply(FrontierWorldState state,SubjectId actor,EvasionEvent event,long tick){
        var prior=state.combat().evasions().get(actor);var rules=state.bootstrap().ruleset().evasion();
        if(event instanceof EvasionEvent.Threatened harm){
            ActorBodyAuthority.requireObservation(state,harm.victim());
            if(!actor.equals(harm.victim().actorId()) || state.actorLocations().get(actor).condition().status()!=ActorLifeStatus.ALIVE)
                throw new IllegalArgumentException("safety harm lost its living observed victim");
var value=prior==null?new Evasion(new SubjectId("safety:"+actor.value().replace(':','-')+"-"+tick),actor,harm.scope(),harm.threat(),harm.threatPosition(),harm.victimPosition(),tick,Math.addExact(tick,rules.quietTicks()),
                1,Optional.empty(),Optional.empty())
                    :prior.observed(harm.threat(),harm.threatPosition(),harm.victimPosition(),tick,rules,true);
            return state.withChanges(FrontierWorldStateUpdate.begin().combat(state.combat().withEvasion(value)));
        }
        if(prior==null)throw new IllegalArgumentException("missing exact safety episode");
        if(event instanceof EvasionEvent.Seen seen){
            ActorBodyAuthority.requireObservation(state,seen.observer());
            if(!actor.equals(seen.observer().actorId()) || !prior.id().equals(seen.episode()))throw new IllegalArgumentException("foreign safety perception");
            if(prior.observedAt()!=seen.expectedObservation())return state;
            return state.withChanges(FrontierWorldStateUpdate.begin().combat(state.combat().withEvasion(prior.observed(prior.threat(),Optional.of(seen.threatPosition()),prior.incidentPosition(),tick,rules,seen.dangerous()))));
        }
        if(event instanceof EvasionEvent.Started started){
            if(!actor.equals(started.actor()) || !prior.id().equals(started.episode()) || prior.execution().isPresent()
                    || ActorExecutionCoordinator.sceneOwns(state,actor))throw new IllegalArgumentException("stale or physically held safety admission");
            var value=prior.started(started.execution());
            return ActorExecutionComposition.LIFECYCLE.prepareBegin(state,started.execution(),tick).commit(state,FrontierWorldStateUpdate.begin().combat(state.combat().withEvasion(value)));
        }
        if(event instanceof EvasionEvent.Destination selected){
            var value=EvasionAuthority.require(state,selected.execution());state.actorExecutions().requireCurrent(selected.execution());
            if(!actor.equals(value.actor()) || value.revision()!=selected.expectedRevision() || state.actorMovements().containsKey(actor))throw new IllegalArgumentException("stale safety destination");
            return state.withChanges(FrontierWorldStateUpdate.begin().combat(state.combat().withEvasion(value.destination(selected.station()))));
        }
        var ended=(EvasionEvent.Ended)event;
        if(!actor.equals(ended.actor()) || !prior.id().equals(ended.episode()) || ended.expectedRevision()!=prior.revision()
                || prior.execution().isPresent() && state.actorMovements().containsKey(actor)
                || state.actorLocations().get(actor).condition().status()==ActorLifeStatus.ALIVE && tick<prior.quietUntil())throw new IllegalArgumentException("safety has no quiet terminal checkpoint");
        var execution=state.actorExecutions();if(prior.execution().isPresent())execution=execution.finish(prior.execution().orElseThrow());
        return state.withChanges(FrontierWorldStateUpdate.begin().combat(state.combat().withoutEvasion(actor)).actorExecutions(execution));
    }
    private EvasionTransitions(){}
}
