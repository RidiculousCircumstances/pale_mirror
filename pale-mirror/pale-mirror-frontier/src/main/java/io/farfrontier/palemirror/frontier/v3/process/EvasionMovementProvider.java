package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import java.util.*;
/** Safety issues a semantic goal; the existing navigator owns all HOT/COLD motion. */
public final class EvasionMovementProvider implements ActorMovementProvider {
    public ActorMovementContext.Provider key(){return ActorMovementContext.Provider.EVASION;}
    private Evasion work(FrontierWorldState state,ActorMovement movement){
        if(!(movement.context() instanceof ActorMovementContext.EvasionLeg leg))throw new IllegalArgumentException("foreign evasion movement context");
        var value=EvasionAuthority.require(state,movement.executionId());state.actorExecutions().requireCurrent(movement.executionId());
        if(!leg.episodeId().equals(value.id()) || leg.revision()!=value.revision() || !value.order().equals(movement.order()))throw new IllegalArgumentException("stale evasion movement");return value;
    }
    public void validate(FrontierWorldState state,ActorMovement movement){work(state,movement);}
    public FrontierWorldState start(FrontierWorldState state,ActorMovement movement,FrontierWorldStateUpdate changes){validate(state,movement);return state.withChanges(changes);}
public KnownPedestrianRouteKnowledge placementKnowledge(FrontierWorldState state,ActorMovement movement){var value=work(state,movement);return KnownPedestrianRouteKnowledge.forPublicJourney(state,value.scope(),
            List.of()).forActor(state,value.actor());}
    public List<SurfaceAnchor> route(FrontierWorldState state,ActorMovement movement,SurfaceAnchor start){return placementKnowledge(state,movement).plannedPath(start,movement.order());}
public void requireRoute(FrontierWorldState state,ActorMovement movement,List<SurfaceAnchor> route){placementKnowledge(state,movement).requireRoute(route);if(!movement.order().arrivedAt(route.getLast()) &&
            route.size()!=TimedKnownRoute.MAX_SURFACES)throw new IllegalArgumentException("escape route lacks its goal or bounded prefix");}
    public List<SurfaceAnchor> coldSegment(FrontierWorldState state,ActorMovement movement,List<SurfaceAnchor> route){return route;}
    public ActorExecutionState arrivalAuthority(FrontierWorldState state,ActorMovement movement){validate(state,movement);return state.actorExecutions();}
    public Optional<BodyPosition> interruptionCheckpoint(FrontierWorldState state,ActorMovement movement,long tick){validate(state,movement);return Optional.of(ActorMovementProcess.bodyAt(state,movement.order().actorId(),tick));}
    public ActorExecutionState interruptionAuthority(FrontierWorldState state,ActorMovement movement){return arrivalAuthority(state,movement);}
    public boolean permitsReplacement(ResidentActivityChoice.Kind next){return false;}
    public List<ProposedEvent> arrived(FrontierWorldState state,ActorMovement movement,long tick){return List.of(EvasionProcess.wake(movement.order().actorId(),tick));}
    public List<ProposedEvent> interrupted(FrontierWorldState state,ActorMovement movement,long tick){return arrived(state,movement,tick);}
}
