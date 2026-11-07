package io.farfrontier.palemirror.frontier.v3.model.clinical;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Bounded clinical obligations, not a parallel health or stock ledger. */
public record ClinicalState(Map<SubjectId,ClinicalWound> wounds,Map<SubjectId,ClinicalTask> tasks,Map<SubjectId,ClinicalHealing> healing,Map<SubjectId,ActorSupplyVisit> supplied){
    public ClinicalState(Map<SubjectId,ClinicalWound> wounds,Map<SubjectId,ClinicalTask> tasks,Map<SubjectId,ClinicalHealing> healing){this(wounds,tasks,healing,Map.of());}
    public ClinicalState {
        wounds=ImmutableLookupMap.copyOf(wounds);tasks=ImmutableLookupMap.copyOf(tasks);healing=ImmutableLookupMap.copyOf(healing);supplied=ImmutableLookupMap.copyOf(supplied);
        if(wounds.size()>SimulationCapacity.MAX_RESIDENTS||tasks.size()>SimulationCapacity.MAX_RESIDENTS||healing.size()>SimulationCapacity.MAX_RESIDENTS
                ||supplied.size()>SimulationCapacity.MAX_RESIDENTS||tasks.values().stream().map(ClinicalTask::actor).distinct().count()!=tasks.size())throw new IllegalArgumentException("competing or unbounded care obligations");
        if(tasks.values().stream().filter(t->t.phase()==ClinicalTask.Phase.TEND).map(ClinicalTask::patient).distinct().count()
                !=tasks.values().stream().filter(t->t.phase()==ClinicalTask.Phase.TEND).count())throw new IllegalArgumentException("competing treatment procedures for one patient");
        supplied.forEach((actor,visit)->{if(!actor.equals(visit.execution().actorId())||visit.pending().isEmpty())throw new IllegalArgumentException("invalid latest completed supply evidence");});
        wounds.forEach((id,v)->{if(!id.equals(v.actor()))throw new IllegalArgumentException("foreign clinical wound");});
        tasks.forEach((id,v)->{if(!id.equals(v.id()))throw new IllegalArgumentException("foreign clinical task");});
        healing.forEach((id,v)->{if(!id.equals(v.actor().execution().actorId()))throw new IllegalArgumentException("foreign clinical recovery");});
        if(tasks.values().stream().filter(v->v.role()==ClinicalTask.Role.MEDIC).map(ClinicalTask::patient).distinct().count()
                !=tasks.values().stream().filter(v->v.role()==ClinicalTask.Role.MEDIC).count())throw new IllegalArgumentException("competing clinicians for one patient");
    }
    public static ClinicalState empty(){return new ClinicalState(Map.of(),Map.of(),Map.of());}
    public ClinicalState with(ClinicalWound wound){return new ClinicalState(ImmutableLookupMap.withEntry(wounds,wound.actor(),wound),tasks,healing,supplied);}
    public ClinicalState with(ClinicalTask task){return new ClinicalState(wounds,ImmutableLookupMap.withEntry(tasks,task.id(),task),healing,supplied);}
    public ClinicalState with(ClinicalHealing value){return new ClinicalState(wounds,tasks,ImmutableLookupMap.withEntry(healing,value.actor().execution().actorId(),value),supplied);}
    public ClinicalState withoutTask(SubjectId task){
        if(Optional.ofNullable(tasks.get(task)).filter(ClinicalTask::pending).isPresent())throw new IllegalArgumentException("clinical procedure remains unsettled");
        return new ClinicalState(wounds,ImmutableLookupMap.withoutEntry(tasks,task),healing,supplied);
    }
    public ClinicalState withoutHealing(SubjectId actor){return new ClinicalState(wounds,tasks,ImmutableLookupMap.withoutEntry(healing,actor),supplied);}
    public ClinicalState withoutWound(SubjectId actor){return new ClinicalState(ImmutableLookupMap.withoutEntry(wounds,actor),tasks,healing,supplied);}
    /** One latest confirmed transfer per actor; retained independently of short task lifetime for native pickup recovery. */
    public ClinicalState supplied(ActorSupplyVisit visit){return new ClinicalState(wounds,tasks,healing,ImmutableLookupMap.withEntry(supplied,visit.execution().actorId(),visit));}
    private static final ImmutableInputView<Map<SubjectId,ClinicalTask>> BY_ACTOR=new ImmutableInputView<>();
    public Optional<ClinicalTask> taskFor(SubjectId actor){return Optional.ofNullable(BY_ACTOR.get(List.of(tasks),()->tasks.values().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(ClinicalTask::actor,t->t))).get(actor));}
}
