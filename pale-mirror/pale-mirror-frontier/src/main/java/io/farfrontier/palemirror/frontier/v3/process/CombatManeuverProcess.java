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
    /** Read-only preview; physical unused-operation cancellation remains the adapter's separate authority. */
    public static MeleeManeuverPlanning.Assessment evasion(FrontierWorldState state,CombatEvent.ManeuverRequested request,long tick){
        var ports=CombatComposition.CAPABILITIES;var order=CombatTransitions.requireOrder(state,ports,request.observer(),tick);
        var capability=ports.require(order.execution());
        if(!order.target().equals(request.target()) || !state.bootstrap().bounds().contains(request.origin().support())
                || !order.engagement().contains(request.origin()))return refused("evasion lost its current target or origin");
        var kind=MeleeDuelPolicy.lateral(state,order,request.facts(),tick,capability);
        if(kind.isEmpty())return refused("no current evasive opportunity");
        var movement=state.actorMovements().get(order.execution().actorId());
        if(movement!=null && !capability.permitsManeuverInterruption(state,movement,order))return refused("evasion cannot replace this movement purpose");
        var assessment=new ActorMovementInterruptionPlanner().assessForOwnerTransition(state,order.execution(),tick);
        if(assessment instanceof ActivityInterruptionPlanner.Waiting)return refused("evasion awaits exact movement checkpoint");
        var following=((ActivityInterruptionPlanner.Ready)assessment).validate(state,order.execution().actorId()).following();
        order=CombatTransitions.requireOrder(following,ports,request.observer(),tick).atActorObservation(request.origin());
        return MeleeManeuverPlanning.assess(following,order,kind.orElseThrow(),request.facts(),tick);
    }
    private static MeleeManeuverPlanning.Assessment refused(String reason){return new MeleeManeuverPlanning.Assessment(Optional.empty(),reason);}
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
        MeleeManeuverPlanning.Assessment destination;
        try{destination=MeleeManeuverPlanning.assess(following,order,kind.orElseThrow(),request.facts(),tick);}
        catch(KnownPedestrianNavigation.RouteUnavailable pending){return FrontierWorldCommandPlanner.rejected("maneuver route "+pending.status());}
        if(destination.candidate().isEmpty())return FrontierWorldCommandPlanner.rejected("no safe bounded maneuver destination: "+destination.detail());
        var candidate=destination.candidate().orElseThrow();
        var selected=new CombatEvent.ManeuverSelected(MeleeManeuvers.selected(following,order,request.observer(),candidate.kind(),candidate.station(),tick));
        CombatTransitions.apply(following,order.owner(),selected,tick,ports);
        var events=new ArrayList<>(ready.events());events.add(new ProposedEvent(order.owner(),selected));events.addAll(capability.wake(following,order.execution(),tick));
        return new CommandPlan.Accepted(List.copyOf(events));
    }
    /** Contact interrupts the same movement owner before closing its approach and preparing the weapon.
     * No arrival is fabricated, and no uncheckpointed movement/attack overlap may enter replay. */
    static CommandPlan contactStrike(FrontierWorldState state,CombatEvent.Prepared prepared,long tick){
        var strike=prepared.strike();var ports=CombatComposition.CAPABILITIES;
        var order=CombatTransitions.requireOrder(state,ports,strike.attacker(),tick);
        var rules=state.bootstrap().ruleset().melee();var maneuver=state.combat().maneuvers().get(order.owner());
        if(!CombatEngagementPlanner.contactHandoff(state.combat(),order,strike.attacker(),strike.attack(),tick,rules))
            return FrontierWorldCommandPlanner.rejected("strike cannot replace this maneuver");
        var movement=state.actorMovements().get(order.execution().actorId());
        if(movement!=null && (!movement.executionId().equals(order.execution()) || !movement.order().equals(maneuver.order())
                || !(movement.context() instanceof ActorMovementContext.CombatManeuverLeg leg)
                || !leg.owner().equals(order.owner()) || leg.attemptOrdinal()!=maneuver.attemptOrdinal()))
            return FrontierWorldCommandPlanner.rejected("contact strike lost its exact approach movement");
        var assessment=new ActorMovementInterruptionPlanner().assessForOwnerTransition(state,order.execution(),tick);
        if(assessment instanceof ActivityInterruptionPlanner.Waiting)
            return FrontierWorldCommandPlanner.rejected("contact strike awaits exact movement checkpoint");
        var ready=((ActivityInterruptionPlanner.Ready)assessment).validate(state,order.execution().actorId());
        var closed=new CombatEvent.ManeuverClosed(order.owner(),maneuver.attemptOrdinal());
        var following=CombatTransitions.apply(ready.following(),order.owner(),closed,tick,ports);
        CombatTransitions.apply(following,order.owner(),prepared,tick,ports);
        var events=new ArrayList<>(ready.events());events.add(new ProposedEvent(order.owner(),closed));
        events.add(new ProposedEvent(order.owner(),prepared));events.addAll(ports.require(order.execution()).wake(following,order.execution(),tick));
        return new CommandPlan.Accepted(List.copyOf(events));
    }
    public static List<ProposedEvent> progress(FrontierWorldState state,ScheduledAction action,ActorExecutionId execution,long tick){
        var m=state.combat().maneuvers().get(execution.activityOwnerId());if(m==null || !m.actor().execution().equals(execution))throw new IllegalArgumentException("foreign maneuver continuation");
        var cap=CombatComposition.CAPABILITIES.require(execution);var rules=state.bootstrap().ruleset().melee().maneuvers();
        if(!m.open())return later(action,Math.max(tick+1,m.closedAt()+rules.recovery(m.kind())));
        boolean permitted=state.actorLocations().get(execution.actorId()).condition().status()==ActorLifeStatus.ALIVE
            && state.actorExecutions().current(execution.activityKind()).get(execution.actorId())!=null
            && state.actorExecutions().current(execution.activityKind()).get(execution.actorId()).equals(execution)
            && CombatComposition.CAPABILITIES.current(state,execution,tick)
                .filter(order->m.matches(order) && MeleeManeuvers.useful(state,order,m.kind(),m.station())).isPresent()
            && ActorBodyAuthority.retainsPhysicalCustody(state,execution.actorId()) && ActorBodyAuthority.current(state,execution.actorId()).equals(m.actor().body());
        var movement=state.actorMovements().get(execution.actorId());
        boolean expired=tick>=(m.launchedAt()==0?m.selectedAt()+80:m.launchedAt()+rules.travelTicks(m.kind()));
        if(!permitted || expired || m.launchedAt()>0 && m.order().arrivedAt(state.actorLocations().get(execution.actorId()).supportingSurface())){
            var result=new ArrayList<ProposedEvent>();
            if(movement!=null){
                if(!(movement.context() instanceof ActorMovementContext.CombatManeuverLeg leg) || leg.attemptOrdinal()!=m.attemptOrdinal())throw new IllegalArgumentException("maneuver lost exclusive route");
                var assessment=new ActorMovementInterruptionPlanner().assessForOwnerTransition(state,execution,tick);
                if(assessment instanceof ActivityInterruptionPlanner.Waiting)return later(action,tick+state.bootstrap().ruleset().melee().sensingInterval());
                result.addAll(((ActivityInterruptionPlanner.Ready)assessment).validate(state,execution.actorId()).events());
            }
            result.add(new ProposedEvent(m.owner(),new CombatEvent.ManeuverClosed(m.owner(),m.attemptOrdinal())));result.addAll(later(action,tick+rules.recovery(m.kind())));return List.copyOf(result);
        }
        if(m.launchedAt()>0 && movement==null){
            var move=new ActorMovement(m.order(),tick,new ActorMovementContext.CombatManeuverLeg(m.owner(),m.attemptOrdinal()),execution);
            var events=new ArrayList<>(ActorMovementProcess.planStart(move));events.addAll(later(action,m.launchedAt()+rules.travelTicks(m.kind())));return List.copyOf(events);
        }
        return later(action,m.launchedAt()==0?m.selectedAt()+80:m.launchedAt()+rules.travelTicks(m.kind()));
    }
    private static List<ProposedEvent> later(ScheduledAction action,long tick){return List.of(new ProposedEvent(action.subject(),new ScheduleEffect.Rescheduled(action.id(),
        new ScheduledAction(action.id(),new SimInstant(tick),action.priority(),action.subject(),action.kind(),action.weight()))));}
    private CombatManeuverProcess(){}
}
