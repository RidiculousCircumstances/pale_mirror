package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.*;
final class CombatProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES=Set.of(CombatEvent.Prepared.TYPE,CombatEvent.Completed.TYPE,CombatEvent.WeaponObserved.TYPE,CombatEvent.PositionBlocked.TYPE,CombatEvent.WeaponDefined.TYPE,CombatEvent.StanceObserved.TYPE,
        CombatEvent.PositionPlanned.TYPE,CombatEvent.ManeuverRequested.TYPE,CombatEvent.ManeuverSelected.TYPE,CombatEvent.ManeuverLaunched.TYPE,CombatEvent.ManeuverClosed.TYPE);
    static final DeterministicProcessDescriptor DESCRIPTOR=new DeterministicProcessDescriptor("combat",TYPES,Set.of(),TYPES,
            java.util.stream.Stream.concat(TYPES.stream(),java.util.stream.Stream.of("kernel.reconsideration_requested","frontier.actor_movement_interrupted",
                    "kernel.schedule_cancelled","kernel.schedule_rescheduled","frontier.actor_movement_started")).collect(java.util.stream.Collectors.toUnmodifiableSet()),TYPES);
    public CommandPlan planCommand(FrontierWorldState state,FrontierCommand command){
        if(!(command.payload() instanceof CombatEvent event))throw new IllegalArgumentException("foreign combat ingress");
        long tick=command.submittedAt().ticks();
        if(event instanceof CombatEvent.ManeuverRequested request){try{return CombatManeuverProcess.request(state,request,tick);}catch(IllegalArgumentException invalid){return FrontierWorldCommandPlanner.rejected(invalid.getMessage());}}
        var events=new ArrayList<ProposedEvent>();var capabilities=CombatComposition.CAPABILITIES;
        SubjectId owner;ActorExecutionId execution;
        if(event instanceof CombatEvent.ManeuverSelected){return FrontierWorldCommandPlanner.rejected("maneuver selection belongs to common admission");}
        if(event instanceof CombatEvent.ManeuverLaunched launched){execution=launched.observer().execution();owner=execution.activityOwnerId();}
        else if(event instanceof CombatEvent.ManeuverClosed closed){owner=closed.owner();var m=state.combat().maneuvers().get(owner);if(m==null)return FrontierWorldCommandPlanner.rejected("no retained maneuver");execution=m.actor().execution();}
        else if(event instanceof CombatEvent.Prepared prepared){owner=prepared.strike().owner();execution=prepared.strike().attacker().execution();}
        else if(event instanceof CombatEvent.Completed outcome){
            owner=outcome.owner();var pending=state.combat().pending().get(owner);
            if(pending==null)return FrontierWorldCommandPlanner.rejected("no exact pending melee outcome");execution=pending.attacker().execution();
        }else if(event instanceof CombatEvent.WeaponDefined defined){execution=defined.execution();owner=execution.activityOwnerId();
        }else if(event instanceof CombatEvent.PositionPlanned position){execution=position.execution();owner=execution.activityOwnerId();
        }else if(event instanceof CombatEvent.StanceObserved stance){execution=stance.stance().observer().execution();owner=execution.activityOwnerId();
        }else{var observer=event instanceof CombatEvent.WeaponObserved value?value.observer():((CombatEvent.PositionBlocked)event).observer();
            execution=observer.execution();owner=execution.activityOwnerId();}
        try{
            if(event instanceof CombatEvent.Prepared prepared){
                if(state.combat().maneuverOpen(owner))return CombatManeuverProcess.contactStrike(state,prepared,tick);
                var move=state.actorMovements().get(execution.actorId());
                if(move!=null){
                    if(!move.executionId().equals(execution) || !capabilities.require(execution).permitsApproach(state,move))
                        return FrontierWorldCommandPlanner.rejected("melee cannot interrupt another activity or mandatory clearance");
                }
            }
            CombatTransitions.apply(state,owner,event,tick,capabilities);
        }catch(IllegalArgumentException invalid){return FrontierWorldCommandPlanner.rejected(invalid.getMessage());}
        events.add(new ProposedEvent(owner,event));events.addAll(capabilities.require(execution).wake(state,execution,tick));
        return new CommandPlan.Accepted(List.copyOf(events));
    }
    public FrontierWorldState reduce(FrontierWorldState state,FrontierEvent event){
        return CombatTransitions.apply(state,event.subject(),(CombatEvent)event.payload(),event.instant().ticks(),CombatComposition.CAPABILITIES);
    }
}
