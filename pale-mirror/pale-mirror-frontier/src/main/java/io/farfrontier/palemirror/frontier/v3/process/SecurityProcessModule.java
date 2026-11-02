package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import java.util.*;
/** Confirmed native perception/attack ingress; guard missions consume its current contacts. */
final class SecurityProcessModule implements FrontierWorldProcessModule {
    private static final Set<String> INGRESS=Set.of(SecurityEvent.Incident.TYPE,SecurityEvent.Seen.TYPE,SecurityEvent.ContactEnded.TYPE,SecurityEvent.Prepared.TYPE,SecurityEvent.Completed.TYPE);
    static final Set<String> TYPES=java.util.stream.Stream.concat(INGRESS.stream(),java.util.stream.Stream.of(SecurityResponsesAllocated.TYPE)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    static final DeterministicProcessDescriptor DESCRIPTOR=new DeterministicProcessDescriptor("security",INGRESS,Set.of(SecurityReview.KIND),TYPES,
            java.util.stream.Stream.concat(TYPES.stream(),java.util.stream.Stream.of("kernel.reconsideration_requested",
                    "frontier.actor_movement_interrupted","kernel.schedule_cancelled","kernel.schedule_rescheduled")).collect(java.util.stream.Collectors.toUnmodifiableSet()),TYPES);
    private static SubjectId subject(SecurityEvent event){return switch(event){
        case SecurityEvent.Incident v->v.home();case SecurityEvent.Seen v->v.contact().home();case SecurityEvent.ContactEnded v->v.home();case SecurityEvent.Prepared v->v.strike().duty();case SecurityEvent.Completed v->v.duty();};}
    public CommandPlan planCommand(FrontierWorldState state,FrontierCommand command){
        if(!(command.payload() instanceof SecurityEvent event))throw new IllegalArgumentException("undeclared security ingress");
        var events=new ArrayList<ProposedEvent>();var owner=subject(event);
        // An observed native melee opportunity may end the optional approach. The common
        // movement owner supplies its safe checkpoint; combat never awards a false arrival.
        try {
            if(event instanceof SecurityEvent.Prepared prepared) {
                var strike=prepared.strike();var movement=state.actorMovements().get(strike.attacker().body().actorId());
                if(movement!=null) {
                    if(!(movement.context() instanceof io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.GuardMissionLeg)
                            || !movement.executionId().equals(strike.attacker().execution()))
                        return FrontierWorldCommandPlanner.rejected("melee cannot interrupt another activity or mandatory equipment clearance");
                    var assessment=ActorMovementProcess.ownerTransitionCheckpoint(state,movement.executionId(),command.submittedAt().ticks());
                    if(assessment instanceof ActivityInterruptionPlanner.Waiting)
                        return FrontierWorldCommandPlanner.rejected("melee is waiting for its shared movement checkpoint");
                    var ready=((ActivityInterruptionPlanner.Ready)assessment).validate(state,movement.order().actorId());
                    events.addAll(ready.events());state=ready.following();
                }
            }
            SecurityTransitions.apply(state,owner,event,command.submittedAt().ticks());
        }
        catch(IllegalArgumentException invalid){return FrontierWorldCommandPlanner.rejected(invalid.getMessage());}
        events.add(new ProposedEvent(owner,event));
        if(event instanceof SecurityEvent.Incident incident)events.add(SecurityReview.wake(incident.home(),command.submittedAt().ticks()));
        if(event instanceof SecurityEvent.Seen seen)events.add(SecurityReview.wake(seen.contact().home(),command.submittedAt().ticks()));
        if(event instanceof SecurityEvent.ContactEnded ended)events.add(SecurityReview.wake(ended.home(),command.submittedAt().ticks()));
        if(event instanceof SecurityEvent.Seen || event instanceof SecurityEvent.ContactEnded) {
            var key=event instanceof SecurityEvent.Seen seen?new SecurityState.ContactKey(seen.contact().home(),seen.contact().target())
                    :new SecurityState.ContactKey(((SecurityEvent.ContactEnded)event).home(),((SecurityEvent.ContactEnded)event).target());
            for(var response:state.security().responses().values())if(response.contact().equals(key))
                events.add(GuardContinuation.wake(response.duty(),command.submittedAt().ticks()));
        }
        if(event instanceof SecurityEvent.Completed completed) {
            events.add(GuardContinuation.wake(completed.duty(),command.submittedAt().ticks()));
            events.add(SecurityReview.wake(state.strategicPlans().guards().get(completed.duty()).home(),command.submittedAt().ticks()));
        }
        return new CommandPlan.Accepted(List.copyOf(events));
    }
    static List<ProposedEvent> review(FrontierWorldState state,ScheduledAction action,long tick) {
        var home=action.subject();var assigned=SecurityDispatch.plan(state,home,tick);
        var previous=state.security().responses().values().stream().filter(r -> r.contact().home().equals(home))
                .collect(java.util.stream.Collectors.toMap(SecurityState.Response::duty,r -> r));
        var events=new ArrayList<ProposedEvent>();
        var rules=state.bootstrap().ruleset().security();
        boolean expired=state.security().awareness().needsPruning(home,tick,rules.investigationTicks(),rules.harmMemoryTicks())
                || state.security().contactsIn(home).stream().anyMatch(c -> !c.current(tick,state.bootstrap().ruleset().security().investigationTicks()));
        if(!previous.equals(assigned) || expired)events.add(new ProposedEvent(home,new SecurityResponsesAllocated(home,assigned)));
        var wake=new java.util.TreeSet<SubjectId>();wake.addAll(previous.keySet());wake.addAll(assigned.keySet());
        for(var id:wake)if(!Objects.equals(previous.get(id),assigned.get(id)))events.add(GuardContinuation.wake(id,tick));
        boolean active=!state.security().awareness().harmsIn(home).isEmpty() || state.security().contactsIn(home).stream().anyMatch(c -> c.current(tick,state.bootstrap().ruleset().security().investigationTicks()));
        events.add(new ProposedEvent(home,active?new ScheduleEffect.Rescheduled(action.id(),SecurityReview.at(home,
                tick+state.bootstrap().ruleset().security().contactRefreshTicks())):new ScheduleEffect.Cancelled(action.id())));
        return List.copyOf(events);
    }
    public FrontierWorldState reduce(FrontierWorldState state,FrontierEvent event){
        if(event.payload() instanceof SecurityResponsesAllocated allocation)return allocation.apply(state,event.subject(),event.instant().ticks());
        return SecurityTransitions.apply(state,event.subject(),(SecurityEvent)event.payload(),event.instant().ticks());
    }
}
