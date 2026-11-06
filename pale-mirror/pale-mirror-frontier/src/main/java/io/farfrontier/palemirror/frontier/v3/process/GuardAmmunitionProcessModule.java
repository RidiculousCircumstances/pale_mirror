package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
final class GuardAmmunitionProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES=Set.of(GuardAmmunitionEvent.Selected.TYPE,GuardAmmunitionEvent.Prepared.TYPE,GuardAmmunitionEvent.Loaded.TYPE,GuardAmmunitionEvent.Cancelled.TYPE,GuardAmmunitionEvent.Aborted.TYPE);
    static final DeterministicProcessDescriptor DESCRIPTOR=new DeterministicProcessDescriptor("guard-ammunition",TYPES,Set.of(),TYPES,
            java.util.stream.Stream.concat(TYPES.stream(),java.util.stream.Stream.of("kernel.reconsideration_requested","frontier.reference_mutation_closed")).collect(java.util.stream.Collectors.toUnmodifiableSet()),TYPES);
    public CommandPlan planCommand(FrontierWorldState state,FrontierCommand command) {
        var event=(GuardAmmunitionEvent)command.payload();long tick=command.submittedAt().ticks();FrontierWorldState after;
        try{after=GuardAmmunition.apply(state,event.duty(),event,tick);}catch(IllegalArgumentException invalid){return FrontierWorldCommandPlanner.rejected(invalid.getMessage());}
        var result=new ArrayList<ProposedEvent>();result.add(new ProposedEvent(event.duty(),event));
        if(event instanceof GuardAmmunitionEvent.Loaded loaded && loaded.step().isPresent())result.addAll(ConfirmedContainerMutationPlan.close(after,List.of(state.strategicPlans().guards().get(event.duty()).source().containerId()),
                command.expectedRevision().next().value()));
        result.add(GuardContinuation.wake(event.duty(),tick));return new CommandPlan.Accepted(List.copyOf(result));
    }
    public FrontierWorldState reduce(FrontierWorldState state,FrontierEvent event){return GuardAmmunition.apply(state,event.subject(),(GuardAmmunitionEvent)event.payload(),event.instant().ticks());}
}
