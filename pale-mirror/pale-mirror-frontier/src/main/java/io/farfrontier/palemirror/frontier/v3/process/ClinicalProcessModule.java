package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.clinical.*;
import java.util.*;
/** Registered trauma owner; shared execution/storage owners retain their own authority. */
final class ClinicalProcessModule implements FrontierWorldProcessModule {
    static final Set<String> TYPES=Set.of(ClinicalEvent.Vitality.TYPE,ClinicalEvent.Started.TYPE,ClinicalEvent.Destination.TYPE,ClinicalEvent.Ended.TYPE,
            ClinicalEvent.SourceSelected.TYPE,ClinicalEvent.SupplyPrepared.TYPE,ClinicalEvent.Supplied.TYPE,ClinicalEvent.SupplyAborted.TYPE,
            ClinicalEvent.TendPrepared.TYPE,ClinicalEvent.Tended.TYPE,ClinicalEvent.HealingPrepared.TYPE,ClinicalEvent.Recovered.TYPE);
    static final Set<String> COMMANDS=Set.of(ClinicalEvent.SupplyPrepared.TYPE,ClinicalEvent.Supplied.TYPE,ClinicalEvent.SupplyAborted.TYPE,
            ClinicalEvent.TendPrepared.TYPE,ClinicalEvent.Tended.TYPE,ClinicalEvent.HealingPrepared.TYPE,ClinicalEvent.Recovered.TYPE);
    static final DeterministicProcessDescriptor DESCRIPTOR=new DeterministicProcessDescriptor("clinical-care",COMMANDS,
            Set.of(ClinicalContinuation.REVIEW,ClinicalContinuation.PROGRESS),TYPES,
            java.util.stream.Stream.concat(TYPES.stream(),java.util.stream.Stream.of("frontier.rest_reserved","frontier.rest_changed","frontier.rest_released",
                "frontier.actor_execution_resumed","frontier.actor_movement_started","frontier.actor_movement_interrupted","kernel.schedule_created","kernel.schedule_rescheduled","kernel.schedule_cancelled",
                "kernel.reconsideration_requested","frontier.reference_mutation_closed")).collect(java.util.stream.Collectors.toUnmodifiableSet()),TYPES);
    public List<ScheduledAction> initialSchedules(FrontierBootstrap b){return b.ruleset().labour().entries().containsKey(ResidentWorkKind.MEDICAL)
            ?b.settlements().stream().map(h->ClinicalContinuation.review(h.id(),b.ruleset().clinical().reviewTicks())).toList():List.of();}
    public CommandPlan planCommand(FrontierWorldState s,FrontierCommand command){
        if(!(command.payload() instanceof ClinicalEvent e))return FrontierWorldCommandPlanner.rejected("foreign clinical payload");
        try{ClinicalTransitions.apply(s,e.subject(),e,command.submittedAt().ticks());}
        catch(IllegalArgumentException invalid){return FrontierWorldCommandPlanner.rejected(invalid.getMessage());}
        var result=new ArrayList<ProposedEvent>();result.add(new ProposedEvent(e.subject(),e));
        if(e instanceof ClinicalEvent.Supplied supplied&&supplied.step().isPresent()){
            var visit=s.humanPopulation().clinical().tasks().get(supplied.task()).supply().orElseThrow();
            var points=ActorSupplyVisits.service(visit,ServiceAccessDemand.Kind.CLINICAL_SUPPLY).stream().map(ServiceAccessDemand.Identity::pointId).toList();
            result.addAll(ConfirmedContainerMutationPlan.close(ClinicalTransitions.apply(s,e.subject(),e,command.submittedAt().ticks()),points,command.expectedRevision().next().value()));
        }
        if(e instanceof ClinicalEvent.Tended tended)result.add(ClinicalContinuation.wake(tended.task(),command.submittedAt().ticks()));
        if(e instanceof ClinicalEvent.Supplied supplied)result.add(ClinicalContinuation.wake(supplied.task(),command.submittedAt().ticks()));
        if(e instanceof ClinicalEvent.SupplyAborted aborted)result.add(ClinicalContinuation.wake(aborted.task(),command.submittedAt().ticks()));
        return new CommandPlan.Accepted(List.copyOf(result));
    }
    public FrontierWorldState reduce(FrontierWorldState s,FrontierEvent event){return ClinicalTransitions.apply(s,event.subject(),(ClinicalEvent)event.payload(),event.instant().ticks());}
}
