package io.farfrontier.palemirror.frontier.v3.model.combat;
/** Pure immediate decision over actual native opportunity; neither routes nor applies damage. */
public final class MeleeTactics {
    public enum Decision { ATTACK, APPROACH, WAIT, FINISHED }
    public record Facts(long tick,boolean targetAlive,boolean visible,boolean inReach,boolean pending,long readyAt,boolean usableWeapon) {
        public Facts {if(tick<0 || readyAt<0)throw new IllegalArgumentException("invalid melee fact time");}
    }
    public static Decision decide(CombatOrder order,Facts facts){
        if(!order.current(facts.tick()) || !facts.targetAlive())return Decision.FINISHED;
        if(facts.pending() || !facts.usableWeapon())return Decision.WAIT;
        if(!facts.visible() || !facts.inReach())return Decision.APPROACH;
        return facts.tick()<facts.readyAt()?Decision.WAIT:Decision.ATTACK;
    }
    private MeleeTactics(){}
}
