package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
/** Read-only explanation of retained facts. Never searches routes or claims a native hit happened. */
public enum CombatStatus {
    IDLE, PENDING_EFFECT, WEAPON_FACTS_PENDING, POSITION_PENDING, REPOSITIONING, APPROACHING, RECOVERING, READY_FOR_NATIVE_CHECK;
    public static CombatStatus read(FrontierWorldState state,CombatCapabilities capabilities,ActorExecutionId execution,long tick){
        if(state.combat().pending().containsKey(execution.activityOwnerId()))return PENDING_EFFECT;
        var order=capabilities.current(state,execution,tick).orElse(null);if(order==null)return IDLE;
        if(MeleePositions.weapon(state.combat(),order).isEmpty())return WEAPON_FACTS_PENDING;
        if(order.station().isEmpty())return POSITION_PENDING;
        if(state.combat().rejected(order,order.station().orElseThrow(),tick,state.bootstrap().ruleset().melee()))return REPOSITIONING;
        var move=state.actorMovements().get(execution.actorId());
        if(move!=null && move.executionId().equals(execution))return APPROACHING;
        return tick<state.combat().nextAttack().getOrDefault(order.owner(),0L)?RECOVERING:READY_FOR_NATIVE_CHECK;
    }
}
