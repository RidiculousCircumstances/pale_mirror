package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;
/** Temporary safety owns a purpose, not the previous work/food/resources or a second body. */
final class SafetyActivityCapability implements ActorActivityCapability {
    public ActorActivityKind kind(){return ActorActivityKind.SAFETY;}
    public Interruption interruption(){return Interruption.TERMINAL_ONLY;}
    public boolean permitsHomeFood(FrontierWorldState state,ActorExecutionId execution){validateReference(state,execution);return false;}
    public void validateReference(FrontierWorldState state,ActorExecutionId execution){EvasionAuthority.require(state,execution);}
    public void validateAmbientRelease(FrontierWorldState state,ActorExecutionId execution){validateReference(state,execution);}
    public ActorActivityBodyCheckpoint bodyCheckpoint(){return ActorActivityBodyCheckpoint.usesActorLocation();}
    public ActorActivityCheckpoint checkpoint(FrontierWorldState state,ActorExecutionId execution){
        validateReference(state,execution);return new ActorActivityCheckpoint(state,execution,Optional.of(new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY,execution.activityOwnerId())));
    }
    public ActorActivityCheckpoint spatialYieldCheckpoint(FrontierWorldState state,ActorExecutionId execution){validateReference(state,execution);return new ActorActivityCheckpoint(state,execution,Optional.empty());}
    public Optional<TravelPace> navigationPace(FrontierWorldState state,ActorExecutionId execution){validateReference(state,execution);return Optional.of(ResidentWalkingPace.read(state,execution.actorId(),LocomotionMode.RUN));}
    public Optional<MovementOrder> navigationOrder(FrontierWorldState state,ActorExecutionId execution){
        validateReference(state,execution);var movement=state.actorMovements().get(execution.actorId());return movement!=null && movement.executionId().equals(execution)?Optional.of(movement.order()):Optional.empty();
    }
public Optional<ActorActivityAmbientTarget> stationaryAmbientTarget(FrontierWorldState state,ActorExecutionId execution){validateReference(state,execution);
            return Optional.of(new ActorActivityAmbientTarget(AmbientGoalKind.PATROL,state.actorLocations().get(execution.actorId()).supportingSurface()));}
    public boolean permitsAmbientMotion(FrontierWorldState state,ActorExecutionId execution,AmbientActorLease lease){
        validateReference(state,execution);return lease.goal()==AmbientGoalKind.PATROL && lease.goalBody().supportingSurface().equals(state.actorLocations().get(execution.actorId()).supportingSurface())
                || lease.goal()==AmbientGoalKind.ACTOR_MOVEMENT && navigationOrder(state,execution).filter(o->o.legalStations().contains(lease.goalBody().supportingSurface())).isPresent();
    }
    public FrontierWorldState pause(FrontierWorldState state,ActorExecutionId execution,long tick){throw new IllegalArgumentException("safety ends through its owner");}
    public FrontierWorldState resume(FrontierWorldState state,ActorExecutionId execution,long tick){throw new IllegalArgumentException("safety has no outer continuation");}
    public FrontierWorldState release(FrontierWorldState state,ActorExecutionId execution){throw new IllegalArgumentException("safety must reach a quiet terminal checkpoint");}
    public Optional<ActorActivityDeath> deathAcknowledgement(){return Optional.of((state,execution,tick)->{
        validateReference(state,execution);var changes=FrontierWorldStateUpdate.begin().combat(state.combat().withoutEvasion(execution.actorId()));
        var move=state.actorMovements().get(execution.actorId());if(move!=null && move.executionId().equals(execution))changes.actorMovements(ImmutableLookupMap.withoutEntry(state.actorMovements(),execution.actorId()));
        return new ActorActivityDeath.Acknowledgement(state,execution,changes,ActorActivityDeath.Disposition.RETIRE_EXACT_EXECUTION);
    });}
}
