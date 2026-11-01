package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.security.*;
import java.util.*;
/** Confirmed native perception/attack ingress; guard missions consume its current contacts. */
final class SecurityProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES=Set.of(SecurityEvent.Incident.TYPE,SecurityEvent.Seen.TYPE,SecurityEvent.ContactEnded.TYPE,SecurityEvent.Prepared.TYPE,SecurityEvent.Completed.TYPE);
    static final DeterministicProcessDescriptor DESCRIPTOR=new DeterministicProcessDescriptor("security",TYPES,Set.of(),TYPES,
            java.util.stream.Stream.concat(TYPES.stream(),java.util.stream.Stream.of("kernel.reconsideration_requested",
                    "frontier.actor_movement_interrupted","kernel.schedule_cancelled")).collect(java.util.stream.Collectors.toUnmodifiableSet()),TYPES);
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
        if(event instanceof SecurityEvent.Seen seen)for(var duty:GuardEquipment.in(state,seen.contact().home()))events.add(GuardContinuation.wake(duty.id(),command.submittedAt().ticks()));
        if(event instanceof SecurityEvent.ContactEnded ended)for(var duty:GuardEquipment.in(state,ended.home()))events.add(GuardContinuation.wake(duty.id(),command.submittedAt().ticks()));
        if(event instanceof SecurityEvent.Completed completed)events.add(GuardContinuation.wake(completed.duty(),command.submittedAt().ticks()));
        return new CommandPlan.Accepted(List.copyOf(events));
    }
    public FrontierWorldState reduce(FrontierWorldState state,FrontierEvent event){return SecurityTransitions.apply(state,event.subject(),(SecurityEvent)event.payload(),event.instant().ticks());}
}
