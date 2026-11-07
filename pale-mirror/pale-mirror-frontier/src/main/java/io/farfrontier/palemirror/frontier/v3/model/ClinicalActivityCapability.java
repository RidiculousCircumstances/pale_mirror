package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.clinical.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;

/** Care owns its checkpoint; sleeping must be physically exited before any successor can move. */
final class ClinicalActivityCapability implements ActorActivityCapability {
    public ActorActivityKind kind(){return ActorActivityKind.CLINICAL_CARE;}
    public Optional<io.farfrontier.palemirror.frontier.v3.model.rest.RestUse.Action> restAction(FrontierWorldState s,ActorExecutionId e,io.farfrontier.palemirror.frontier.v3.model.rest.RestUse use,long tick){
        var task=ClinicalAuthority.require(s,e);var reserved=io.farfrontier.palemirror.frontier.v3.model.rest.RestUse.Phase.RESERVED;
        if(use.phase()!=reserved&&(!s.actorLocations().get(e.actorId()).supportingSurface().equals(new SurfaceAnchor(use.place().head()))||!io.farfrontier.palemirror.frontier.v3.model.rest.RestPlaces.present(s,use)||
            s.humanPopulation().restPlaces().unavailable().contains(use.place().id())||!ClinicalAuthority.wantsRest(s,task.actor())
                ||io.farfrontier.palemirror.frontier.v3.process.ClinicalCareProcess.hungry(s,task.actor(),tick)||s.combat().evasions().containsKey(task.actor())||task.phase()==ClinicalTask.Phase.SUPPLY))
            return Optional.of(io.farfrontier.palemirror.frontier.v3.model.rest.RestUse.Action.EXIT);
        if(task.phase()==ClinicalTask.Phase.REST&&ClinicalAuthority.wantsRest(s,task.actor()))return Optional.of(use.phase()==reserved
                ?io.farfrontier.palemirror.frontier.v3.model.rest.RestUse.Action.ENTER:io.farfrontier.palemirror.frontier.v3.model.rest.RestUse.Action.RESTORE);
        return Optional.empty();
    }
    public void validateRestReference(FrontierWorldState s,ActorExecutionId e,io.farfrontier.palemirror.frontier.v3.model.rest.RestUse use){
        var task=ClinicalAuthority.require(s,e);
        if(task.role()!=ClinicalTask.Role.PATIENT||!use.execution().equals(e)||!use.place().home().equals(task.home()))throw new IllegalArgumentException("rest place has no exact patient stay");
    }
    public boolean permitsRestRelease(FrontierWorldState s,ActorExecutionId e,io.farfrontier.palemirror.frontier.v3.model.rest.RestUse use){
        ClinicalAuthority.require(s,e);var wound=s.humanPopulation().clinical().wounds().get(e.actorId());
        return use.execution().equals(e)&&(!ClinicalAuthority.alive(s,e.actorId())||wound==null||use.completed().filter(i->i.end()>wound.evaluatedAt()).isEmpty());
    }
    public boolean permitsRest(FrontierWorldState s,ActorExecutionId e,io.farfrontier.palemirror.frontier.v3.model.rest.RestUse use){
        var task=ClinicalAuthority.require(s,e);return task.role()==ClinicalTask.Role.PATIENT&&use.execution().equals(e)&&use.place().home().equals(task.home())&&ClinicalAuthority.wantsRest(s,task.actor())
            &&Optional.ofNullable(s.humanPopulation().restPlaces().uses().get(task.actor())).flatMap(io.farfrontier.palemirror.frontier.v3.model.rest.RestUse::completed)
                .map(interval->s.humanPopulation().clinical().wounds().get(task.actor()).evaluatedAt()>=interval.end()).orElse(true);
    }
    public Interruption interruption(){return Interruption.RETAIN_CONTINUATION;}
    public void validateReference(FrontierWorldState s,ActorExecutionId e){ClinicalAuthority.require(s,e);}
    public ActorActivityCheckpoint checkpoint(FrontierWorldState s,ActorExecutionId e){
        var task=ClinicalAuthority.require(s,e);
        return new ActorActivityCheckpoint(s,e,ClinicalAuthority.pending(s,task)
                ?Optional.of(new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION,task.id()))
                :s.actorMovements().containsKey(task.actor())?Optional.of(new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY,task.id())):Optional.empty());
    }
    public void validateAmbientRelease(FrontierWorldState s,ActorExecutionId e){
        if(ClinicalAuthority.require(s,e).pending()||ClinicalAuthority.pendingOwner(s,e.actorId()).isPresent())throw new IllegalArgumentException("clinical physical effect remains unsettled");
    }
    public ActorActivityBodyCheckpoint bodyCheckpoint(){return request->{validateAmbientRelease(request.expectedState(),request.execution());return new ActorMovementBodyCheckpoint().acknowledge(request);};}
    public Optional<ActorActivityAmbientTarget> stationaryAmbientTarget(FrontierWorldState s,ActorExecutionId e){validateReference(s,e);return Optional.of(new ActorActivityAmbientTarget(AmbientGoalKind.CLINICAL_CARE,
        s.actorLocations().get(e.actorId()).supportingSurface()));}
    public Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.TravelPace> navigationPace(FrontierWorldState s,ActorExecutionId e){validateReference(s,e);return Optional.of(ResidentWalkingPace.read(s,e.actorId(),
        io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode.WALK));}
    public Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder> navigationOrder(FrontierWorldState s,ActorExecutionId e){
        validateReference(s,e);var movement=s.actorMovements().get(e.actorId());return movement!=null&&movement.executionId().equals(e)?Optional.of(movement.order()):Optional.empty();
    }
    public boolean permitsAmbientMotion(FrontierWorldState s,ActorExecutionId e,AmbientActorLease lease){
        validateReference(s,e);
        return lease.goal()==AmbientGoalKind.CLINICAL_CARE&&lease.goalBody().equals(s.actorLocations().get(e.actorId()).body())
                ||lease.goal()==AmbientGoalKind.ACTOR_MOVEMENT&&navigationOrder(s,e).filter(o->o.legalStations().contains(lease.goalBody().supportingSurface())).isPresent();
    }
    public ActorActivityResumption resumptionReference(){return request->{
        var s=request.expectedState();var task=ClinicalAuthority.require(s,request.suspended());var rest=s.humanPopulation().restPlaces();
        var use=rest.uses().get(task.actor());if(use!=null)rest=rest.with(use.resumed(request.successor()));
        return new ActorActivityResumption.Acknowledgement(request,FrontierWorldStateUpdate.begin().humanPopulation(s.humanPopulation()
                .withClinical(s.humanPopulation().clinical().with(task.resumed(request.successor()))).withRestPlaces(rest)));
    };}
    public Optional<ActorActivityDeath> deathAcknowledgement(){return Optional.of((s,e,tick)->{
        validateReference(s,e);var movements=s.actorMovements();var movement=movements.get(e.actorId());
        if(movement!=null&&movement.executionId().equals(e))movements=ImmutableLookupMap.withoutEntry(movements,e.actorId());
        // Exact preparation survives death until its native receipt is settled. Nothing heals a dead actor.
        return new ActorActivityDeath.Acknowledgement(s,e,FrontierWorldStateUpdate.begin().actorMovements(movements),ActorActivityDeath.Disposition.RETIRE_EXACT_EXECUTION);
    });}
    public List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> continuationAfterResume(FrontierWorldState s,ActorExecutionId e,long tick){validateReference(s,e);return List.of(ClinicalContinuation.wake(e.activityOwnerId(),tick));}
    public FrontierWorldState pause(FrontierWorldState s,ActorExecutionId e,long tick){
        if(!checkpoint(s,e).ready())throw new IllegalArgumentException("care has no safe handoff");
        var task=ClinicalAuthority.require(s,e);return ClinicalAuthority.update(s,s.humanPopulation().clinical().with(task.phase(ClinicalTask.Phase.SELECT,s.actorLocations().get(task.actor()).supportingSurface(),tick)));
    }
    public FrontierWorldState resume(FrontierWorldState s,ActorExecutionId e,long tick){validateReference(s,e);return s;}
    public FrontierWorldState release(FrontierWorldState s,ActorExecutionId e){throw new IllegalArgumentException("clinical care requires its explicit terminal settlement");}
}
