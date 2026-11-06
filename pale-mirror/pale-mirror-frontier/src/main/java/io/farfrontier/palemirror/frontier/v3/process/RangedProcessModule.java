package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import java.util.*;
final class RangedProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES=Set.of(RangedEvent.Observed.TYPE,RangedEvent.Prepared.TYPE,RangedEvent.Completed.TYPE);
    static final DeterministicProcessDescriptor DESCRIPTOR=new DeterministicProcessDescriptor("ranged-combat",TYPES,Set.of(),TYPES,
            java.util.stream.Stream.concat(TYPES.stream(),java.util.stream.Stream.of("kernel.reconsideration_requested")).collect(java.util.stream.Collectors.toUnmodifiableSet()),TYPES);
    public CommandPlan planCommand(FrontierWorldState state,FrontierCommand command) {
        if(!(command.payload() instanceof RangedEvent event))throw new IllegalArgumentException("foreign ranged ingress");
        if(event instanceof RangedEvent.Completed v && !state.combat().ranged().pending().containsKey(v.owner()))return FrontierWorldCommandPlanner.rejected("no exact pending ranged shot");
        var execution=event instanceof RangedEvent.Prepared v?v.shot().attacker().execution():event instanceof RangedEvent.Observed v?v.stance().observer().execution()
                :Optional.ofNullable(state.combat().ranged().pending().get(((RangedEvent.Completed)event).owner())).orElseThrow(()->new IllegalArgumentException("missing pending shot")).attacker().execution();
        try{RangedTransitions.apply(state,execution.activityOwnerId(),event,command.submittedAt().ticks(),CombatComposition.CAPABILITIES);}
        catch(IllegalArgumentException invalid){return FrontierWorldCommandPlanner.rejected(invalid.getMessage());}
        var result=new ArrayList<ProposedEvent>();result.add(new ProposedEvent(execution.activityOwnerId(),event));
        result.addAll(CombatComposition.CAPABILITIES.require(execution).wake(state,execution,command.submittedAt().ticks()));return new CommandPlan.Accepted(List.copyOf(result));
    }
    public FrontierWorldState reduce(FrontierWorldState state,FrontierEvent event){return RangedTransitions.apply(state,event.subject(),(RangedEvent)event.payload(),event.instant().ticks(),CombatComposition.CAPABILITIES);}
}
