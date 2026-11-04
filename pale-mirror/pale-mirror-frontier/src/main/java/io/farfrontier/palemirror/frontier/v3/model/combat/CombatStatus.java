package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
/** Read-only explanation of retained facts. Never searches routes or claims a native hit happened. */
public enum CombatStatus {
    IDLE, EXHAUSTED, COORDINATING_ATTACK, WINDING_UP, HOLDING,
     ROUTE_PENDING, WAITING_SPACE, PATH_UNAVAILABLE, TARGET_UNREACHABLE, PENDING_EFFECT, WEAPON_FACTS_PENDING, POSITION_PENDING, REPOSITIONING, APPROACHING, RECOVERING, READY_FOR_NATIVE_CHECK;
    public static CombatStatus read(FrontierWorldState state,CombatCapabilities capabilities,ActorExecutionId execution,long tick){
        var strike=state.combat().pending().get(execution.activityOwnerId());
        if(strike!=null)return tick<strike.impactAt(state.bootstrap().ruleset().melee())?WINDING_UP:PENDING_EFFECT;
        var order=capabilities.current(state,execution,tick).orElse(null);if(order==null)return IDLE;
        if(MeleePositions.weapon(state.combat(),order).isEmpty())return WEAPON_FACTS_PENDING;
        if(!state.combat().affords(order.owner(),tick,state.bootstrap().ruleset().melee().readiness().quickCost(),state.bootstrap().ruleset().melee()))return EXHAUSTED;
        var stance=state.combat().stances().get(order.owner());
        if(stance!=null && stance.current(order,tick,state.bootstrap().ruleset().melee()) && stance.attackReady()){
            if(tick<state.combat().nextAttack().getOrDefault(order.owner(),0L))return RECOVERING;
            return CombatEngagementPlanner.permitsStrike(state.combat(),order,stance.observer(),stance.holding()?MeleeAttack.QUICK:MeleeAttack.LUNGE,tick,state.bootstrap().ruleset().melee())
                    ?READY_FOR_NATIVE_CHECK:COORDINATING_ATTACK;
        }
        if(MeleePositions.holding(state,order,tick))return HOLDING;
        var point=state.combat().positions().get(order.owner());
        if(point!=null && point.matches(order))switch(point.reason()){
            case ROUTE_PENDING -> {return ROUTE_PENDING;}
            case SPACE_OCCUPIED -> {return WAITING_SPACE;}
            case NO_PATH,NO_POSITION -> {return PATH_UNAVAILABLE;}
            case UNREACHABLE -> {if(point.deferred(order,tick))return TARGET_UNREACHABLE;}
            case RESERVED -> { }
        }
        if(order.station().isEmpty())return POSITION_PENDING;
        if(state.combat().rejected(order,order.station().orElseThrow(),tick,state.bootstrap().ruleset().melee()))return REPOSITIONING;
        var move=state.actorMovements().get(execution.actorId());
        if(move!=null && move.executionId().equals(execution))return APPROACHING;
        return tick<state.combat().nextAttack().getOrDefault(order.owner(),0L)?RECOVERING:READY_FOR_NATIVE_CHECK;
    }
}
