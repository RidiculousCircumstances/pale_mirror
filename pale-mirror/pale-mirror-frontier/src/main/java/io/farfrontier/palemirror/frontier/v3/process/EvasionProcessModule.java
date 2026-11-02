package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import java.util.*;
final class EvasionProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES=Set.of(EvasionEvent.Threatened.TYPE,EvasionEvent.Seen.TYPE,EvasionEvent.Started.TYPE,EvasionEvent.Destination.TYPE,EvasionEvent.Ended.TYPE);
    static final DeterministicProcessDescriptor DESCRIPTOR=new DeterministicProcessDescriptor("evasion",Set.of(EvasionEvent.Seen.TYPE),Set.of(EvasionProcess.REVIEW),TYPES,
java.util.stream.Stream.concat(TYPES.stream(),java.util.stream.Stream.of("kernel.reconsideration_requested","kernel.schedule_cancelled","kernel.schedule_rescheduled","kernel.schedule_created",
                "frontier.actor_movement_started","frontier.actor_movement_interrupted")).collect(java.util.stream.Collectors.toUnmodifiableSet()),TYPES);
    public CommandPlan planCommand(FrontierWorldState state,FrontierCommand command){
        if(!(command.payload() instanceof EvasionEvent.Seen seen))throw new IllegalArgumentException("undeclared evasion ingress");
        var actor=seen.observer().actorId();try{EvasionTransitions.apply(state,actor,seen,command.submittedAt().ticks());}
        catch(IllegalArgumentException rejected){return FrontierWorldCommandPlanner.rejected(rejected.getMessage());}
        return new CommandPlan.Accepted(List.of(new ProposedEvent(actor,seen),EvasionProcess.wake(actor,command.submittedAt().ticks())));
    }
    public FrontierWorldState reduce(FrontierWorldState state,FrontierEvent event){
        var next=EvasionTransitions.apply(state,event.subject(),(EvasionEvent)event.payload(),event.instant().ticks());
        return event.payload() instanceof EvasionEvent.Started || event.payload() instanceof EvasionEvent.Ended
                ?ResidentActivityProcess.retargetHotResident(next,event.subject(),event.instant().ticks()):next;
    }
}
