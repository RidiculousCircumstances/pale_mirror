package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;
public final class EvasionAuthority {
    public static Evasion require(FrontierWorldState state,ActorExecutionId execution){
        var value=state.combat().evasions().get(execution.actorId());
        if(execution.activityKind()!=ActorActivityKind.SAFETY || value==null || !value.execution().equals(Optional.of(execution)))
            throw new IllegalArgumentException("safety lost its exact episode/execution");
        return value;
    }
    public static void validate(CombatState combat,Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId,ActorLocation> actors,ActorExecutionState executions){
        for(var value:combat.evasions().values()){
            var actor=actors.get(value.actor());if(actor==null)throw new IllegalArgumentException("safety lost actor");
            value.execution().ifPresent(executions::requireRetained);
        }
        for(var execution:executions.current(ActorActivityKind.SAFETY).values())
            if(Optional.ofNullable(combat.evasions().get(execution.actorId())).flatMap(Evasion::execution).filter(execution::equals).isEmpty())
                throw new IllegalArgumentException("safety execution lost episode");
    }
    private EvasionAuthority(){}
}
