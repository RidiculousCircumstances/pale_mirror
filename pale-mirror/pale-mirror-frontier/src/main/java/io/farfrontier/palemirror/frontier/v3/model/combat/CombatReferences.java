package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import java.util.Map;
/** Shared actor/execution closure; historical weapon facts do not authorize a new action. */
public final class CombatReferences {
    public static void validate(CombatState combat,Map<SubjectId,ActorLocation> actors,ActorExecutionState executions,CombatCapabilities capabilities){
        EvasionAuthority.validate(combat,actors,executions);
        for(var arm:combat.armaments().values())if(!actors.containsKey(arm.execution().actorId()))throw new IllegalArgumentException("combat history lost its actor");
        for(var point:combat.positions().values())if(!actors.containsKey(point.execution().actorId()))throw new IllegalArgumentException("melee position lost actor");
        for(var stance:combat.stances().values())if(!actors.containsKey(stance.observer().execution().actorId()))throw new IllegalArgumentException("melee stance lost actor");
        for(var encounter:combat.engagements().values())for(var member:encounter.participants().values()){
            capabilities.require(member.execution());
            if(!actors.containsKey(member.execution().actorId()))throw new IllegalArgumentException("encounter lost actor");
            executions.requireRetained(member.execution());
        }
        for(var strike:combat.pending().values()){
            capabilities.require(strike.attacker().execution());
            var actor=actors.get(strike.attacker().body().actorId());
            if(actor==null)throw new IllegalArgumentException("pending melee lost its actor");
            if(actor.condition().status()==ActorLifeStatus.ALIVE)executions.requireRetained(strike.attacker().execution());
        }
    }
    private CombatReferences(){}
}
