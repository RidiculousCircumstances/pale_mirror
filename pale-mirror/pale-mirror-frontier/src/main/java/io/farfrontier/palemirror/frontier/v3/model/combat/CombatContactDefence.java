package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;

/** Contact permission for a ranged combatant, never a melee pursuit or a second physical executor. */
public final class CombatContactDefence {
    public static boolean learned(CombatProfile profile){return profile.skills().containsKey(CombatSkillId.CROSSBOW)
            && profile.knows(CombatSkillId.CROSSBOW,CombatTrainingCatalog.standard().require(MeleeAttack.KICK).minimum());}
    public static boolean permits(CombatOrder order,MeleeAttack attack){
        return order.use() instanceof CombatUse.Crossbow && attack==MeleeAttack.KICK
                && learned(order.profile());
    }
    public static boolean ready(FrontierWorldState state,CombatOrder order,ActorActuationId observer,long tick){
        var combat=state.combat();var rules=state.bootstrap().ruleset().melee();var stance=combat.stances().get(order.owner());
        return permits(order,MeleeAttack.KICK) && observer.execution().equals(order.execution())
                && stance!=null && stance.observer().equals(observer) && stance.current(order,tick,rules) && stance.attackReady()
                && !combat.pending().containsKey(order.owner()) && !combat.ranged().unsettled(order.owner()) && !combat.maneuverOpen(order.owner())
                && tick>=combat.nextAttack().getOrDefault(order.owner(),0L) && tick>=combat.ranged().nextAttack().getOrDefault(order.owner(),0L)
                && combat.affords(order.owner(),tick,CombatMechanics.cost(order.profile(),rules.readiness().cost(MeleeAttack.KICK)),rules);
    }
    private CombatContactDefence(){}
}
