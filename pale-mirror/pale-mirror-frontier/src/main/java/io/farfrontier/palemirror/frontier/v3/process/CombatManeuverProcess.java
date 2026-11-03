package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;
/** Common bounded maneuver lifecycle. No appointment, attack effect or native motion writer. */
public final class CombatManeuverProcess {
    static CommandPlan request(FrontierWorldState state,CombatEvent.ManeuverRequested request,long tick){
        var ports=CombatComposition.CAPABILITIES;var order=CombatTransitions.requireOrder(state,ports,request.observer(),tick);
        if(!order.target().equals(request.target()))return FrontierWorldCommandPlanner.rejected("maneuver target changed");
        if(!state.bootstrap().bounds().contains(request.origin().support()) || !order.engagement().contains(request.origin()))
            return FrontierWorldCommandPlanner.rejected("observed maneuver origin is outside the permitted engagement");
        var capability=ports.require(order.execution());
        var kind=MeleeManeuvers.preference(state,order,request.facts(),tick,capability);
        if(kind.isEmpty())return FrontierWorldCommandPlanner.rejected("no current maneuver opportunity");
        var currentMove=state.actorMovements().get(order.execution().actorId());
        if(currentMove!=null && !capability.permitsManeuverInterruption(state,currentMove,order))return FrontierWorldCommandPlanner.rejected("maneuver cannot replace another movement purpose");
        var assessment=new ActorMovementInterruptionPlanner().assessForOwnerTransition(state,order.execution(),tick);
        if(assessment instanceof ActivityInterruptionPlanner.Waiting)return FrontierWorldCommandPlanner.rejected("maneuver awaits exact movement checkpoint");
        var ready=((ActivityInterruptionPlanner.Ready)assessment).validate(state,order.execution().actorId());var following=ready.following();
        order=CombatTransitions.requireOrder(following,ports,request.observer(),tick).atActorObservation(request.origin());
        Optional<SurfaceAnchor> station;
        try{station=MeleeManeuvers.destination(following,order,kind.orElseThrow(),capability.maneuverKnowledge(following,order.execution()),tick);}
        catch(KnownPedestrianNavigation.RouteUnavailable pending){return FrontierWorldCommandPlanner.rejected("maneuver route "+pending.status());}
        if(station.isEmpty())return FrontierWorldCommandPlanner.rejected("no safe bounded maneuver destination");
        var selected=new CombatEvent.ManeuverSelected(MeleeManeuvers.selected(following,order,request.observer(),kind.orElseThrow(),station.orElseThrow(),tick));
        CombatTransitions.apply(following,order.owner(),selected,tick,ports);
        var events=new ArrayList<>(ready.events());events.add(new ProposedEvent(order.owner(),selected));events.addAll(capability.wake(following,order.execution(),tick));
        return new CommandPlan.Accepted(List.copyOf(events));
    }
    public static List<ProposedEvent> progress(FrontierWorldState state,ScheduledAction action,ActorExecutionId execution,long tick){
        var m=state.combat().maneuvers().get(execution.activityOwnerId());if(m==null || !m.actor().execution().equals(execution))throw new IllegalArgumentException("foreign maneuver continuation");
        var cap=CombatComposition.CAPABILITIES.require(execution);var rules=state.bootstrap().ruleset().melee().maneuvers();
        if(!m.open())return later(action,Math.max(tick+1,m.closedAt()+rules.recovery()));
        boolean permitted=state.actorLocations().get(execution.actorId()).condition().status()==ActorLifeStatus.ALIVE
            && state.actorExecutions().current(execution.activityKind()).get(execution.actorId())!=null
            && state.actorExecutions().current(execution.activityKind()).get(execution.actorId()).equals(execution)
            && CombatComposition.CAPABILITIES.current(state,execution,tick).filter(m::matches).isPresent()
            && ActorBodyAuthority.retainsPhysicalCustody(state,execution.actorId()) && ActorBodyAuthority.current(state,execution.actorId()).equals(m.actor().body());
        var movement=state.actorMovements().get(execution.actorId());
        boolean expired=tick>=(m.launchedAt()==0?m.selectedAt()+80:m.launchedAt()+rules.travelTicks());
        if(!permitted || expired || m.launchedAt()>0 && m.order().arrivedAt(state.actorLocations().get(execution.actorId()).supportingSurface())){
            var result=new ArrayList<ProposedEvent>();
            if(movement!=null){
                if(!(movement.context() instanceof ActorMovementContext.CombatManeuverLeg leg) || leg.attemptOrdinal()!=m.attemptOrdinal())throw new IllegalArgumentException("maneuver lost exclusive route");
                var assessment=new ActorMovementInterruptionPlanner().assessForOwnerTransition(state,execution,tick);
                if(assessment instanceof ActivityInterruptionPlanner.Waiting)return later(action,tick+state.bootstrap().ruleset().melee().sensingInterval());
                result.addAll(((ActivityInterruptionPlanner.Ready)assessment).validate(state,execution.actorId()).events());
            }
            result.add(new ProposedEvent(m.owner(),new CombatEvent.ManeuverClosed(m.owner(),m.attemptOrdinal())));result.addAll(later(action,tick+rules.recovery()));return List.copyOf(result);
        }
        if(m.launchedAt()>0 && movement==null){
            var move=new ActorMovement(m.order(),tick,new ActorMovementContext.CombatManeuverLeg(m.owner(),m.attemptOrdinal()),execution);
            var events=new ArrayList<>(ActorMovementProcess.planStart(move));events.addAll(later(action,m.launchedAt()+rules.travelTicks()));return List.copyOf(events);
        }
        return later(action,m.launchedAt()==0?m.selectedAt()+80:m.launchedAt()+rules.travelTicks());
    }
    private static List<ProposedEvent> later(ScheduledAction action,long tick){return List.of(new ProposedEvent(action.subject(),new ScheduleEffect.Rescheduled(action.id(),
        new ScheduledAction(action.id(),new SimInstant(tick),action.priority(),action.subject(),action.kind(),action.weight()))));}
    private CombatManeuverProcess(){}
}
