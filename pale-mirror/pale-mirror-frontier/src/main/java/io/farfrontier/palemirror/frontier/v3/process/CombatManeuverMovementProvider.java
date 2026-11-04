package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import java.util.*;
/** One registered tactical purpose over the existing movement owner, for any combat-capable role. */
public final class CombatManeuverMovementProvider implements ActorMovementProvider {
    public ActorMovementContext.Provider key(){return ActorMovementContext.Provider.COMBAT_MANEUVER;}
    private MeleeManeuver work(FrontierWorldState state,ActorMovement move){
        if(!(move.context() instanceof ActorMovementContext.CombatManeuverLeg leg))throw new IllegalArgumentException("foreign maneuver movement");
        var value=state.combat().maneuvers().get(move.order().ownerId());
        if(value==null || !value.open() || value.launchedAt()==0 || value.attemptOrdinal()!=leg.attemptOrdinal() || !value.owner().equals(leg.owner())
                || !value.actor().execution().equals(move.executionId()) || !value.order().equals(move.order()))throw new IllegalArgumentException("stale maneuver goal/execution");
        return value;
    }
    public void validate(FrontierWorldState state,ActorMovement move){work(state,move);}
    public FrontierWorldState start(FrontierWorldState state,ActorMovement move,FrontierWorldStateUpdate update){validate(state,move);return state.withChanges(update);}
    public KnownPedestrianRouteKnowledge placementKnowledge(FrontierWorldState state,ActorMovement move){var m=work(state,move);var port=CombatComposition.CAPABILITIES.require(move.executionId());
        // Permission may have expired while a retained route is being safely interrupted.
        port.requireReference(state,move.executionId());return port.maneuverKnowledge(state,m.actor().execution());}
    public List<SurfaceAnchor> route(FrontierWorldState state,ActorMovement move,SurfaceAnchor start){return placementKnowledge(state,move).plannedPath(start,move.order());}
    public void requireRoute(FrontierWorldState state,ActorMovement move,List<SurfaceAnchor> route){placementKnowledge(state,
        move).requireRoute(route);if(!move.order().arrivedAt(route.getLast()))throw new IllegalArgumentException("maneuver requires its short complete route");
        }
    public List<SurfaceAnchor> coldSegment(FrontierWorldState state,ActorMovement move,List<SurfaceAnchor> route){validate(state,move);return List.of(route.getFirst());}
    public List<SurfaceAnchor> nextSegment(FrontierWorldState state,ActorMovement move,SurfaceAnchor start){validate(state,move);return List.of(start);}
    public MovementPermission movementPermission(FrontierWorldState state,ActorMovement move,SurfaceAnchor actual,long tick,ActorPositionView positions){
        var m=work(state,move);var rules=state.bootstrap().ruleset().melee().maneuvers();
        if(!ActorBodyAuthority.retainsPhysicalCustody(state,move.executionId().actorId())
                || !ActorBodyAuthority.current(state,move.executionId().actorId()).equals(m.actor().body())
                || tick<m.launchedAt() || tick>=m.launchedAt()+rules.travelTicks(m.kind()) || CombatComposition.CAPABILITIES.current(state,move.executionId(),tick).filter(m::matches).isEmpty())
            return MovementPermission.hold(MovementPermission.Reason.INACTIVE_MEMBER);
        return MovementPermission.allow(pace(state,m));
    }
    private TravelPace pace(FrontierWorldState state,MeleeManeuver m){var normal=ActorExecutionComposition.CAPABILITIES.require(m.actor().execution().activityKind()).navigationPace(state,m.actor().execution()).orElseThrow();
        var r=state.bootstrap().ruleset().melee().maneuvers();return new TravelPace(normal.blocksPerTick()*r.pacePermille(m.kind())/1000.0);}
    public long ticksPerEdge(FrontierWorldState state,ActorMovement move){return Math.max(1,(long)Math.ceil(1/pace(state,work(state,move)).blocksPerTick()));}
    public ActorExecutionState arrivalAuthority(FrontierWorldState state,ActorMovement move){validate(state,move);return state.actorExecutions();}
    public ActorExecutionState interruptionAuthority(FrontierWorldState state,ActorMovement move){return arrivalAuthority(state,move);}
    public Optional<BodyPosition> interruptionCheckpoint(FrontierWorldState state,ActorMovement move,long tick){validate(state,move);return Optional.of(ActorMovementProcess.bodyAt(state,move.order().actorId(),tick));}
    public boolean permitsReplacement(ResidentActivityChoice.Kind next){return false;}
    public List<ProposedEvent> arrived(FrontierWorldState state,ActorMovement move,long tick){work(state,move);return CombatComposition.CAPABILITIES.require(move.executionId()).wake(state,move.executionId(),tick);}
    public List<ProposedEvent> interrupted(FrontierWorldState state,ActorMovement move,long tick){return arrived(state,move,tick);}
}
