package io.farfrontier.palemirror.frontier.v3.model.execution;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;
/** One current authority and a bounded LIFO chain of exact owner continuations. */
public record ActorExecution(SubjectId actorId,long generation,Optional<ActorExecutionId> current,
        List<ActorExecutionId> continuations) {
    public static final int MAX_CONTINUATIONS=4;
    public ActorExecution(SubjectId actorId,long generation,Optional<ActorExecutionId> current,Optional<ActorExecutionId> suspended) {
        this(actorId,generation,current,suspended.stream().toList());
    }
    public ActorExecution {
        Objects.requireNonNull(actorId);Objects.requireNonNull(current);continuations=List.copyOf(continuations);
        if(generation<1 || continuations.size()>MAX_CONTINUATIONS)throw new IllegalArgumentException("execution continuation bound");
        current.ifPresent(id->{if(!id.actorId().equals(actorId)||id.generation()!=generation)throw new IllegalArgumentException("foreign current execution");});
        var owners=new HashSet<java.util.Map.Entry<ActorActivityKind,SubjectId>>();
        current.ifPresent(id->owners.add(Map.entry(id.activityKind(),id.activityOwnerId())));
        long upper=current.map(ActorExecutionId::generation).orElse(generation+1);
        for(var id:continuations){
            if(!id.actorId().equals(actorId)||id.generation()>=upper || !owners.add(Map.entry(id.activityKind(),id.activityOwnerId())))
                throw new IllegalArgumentException("foreign, unordered or competing continuation");
            upper=id.generation();
        }
    }
    /** The immediate continuation, never discovery of an arbitrary older owner. */
    public Optional<ActorExecutionId> suspended(){return continuations.stream().findFirst();}
}
