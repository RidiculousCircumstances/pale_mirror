package io.farfrontier.palemirror.frontier.v3.model.combat;
import java.util.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
/** Pure immediate decision over actual native opportunity; neither routes nor applies damage. */
public final class MeleeTactics {
    public enum Decision { ATTACK, APPROACH, WAIT, FINISHED }
    public record Facts(long tick,boolean targetAlive,boolean visible,boolean inReach,boolean pending,long readyAt,boolean usableWeapon) {
        public Facts {if(tick<0 || readyAt<0)throw new IllegalArgumentException("invalid melee fact time");}
    }
    /** Native boundary supplies observed separation, never a player's future inputs. */
    public static MeleeAttack attack(WeaponProfile weapon,double distance,double separatingSpeed,MeleeRules rules){
        if(!Double.isFinite(distance) || distance<0 || !Double.isFinite(separatingSpeed))
            throw new IllegalArgumentException("invalid observed melee separation");
        // An already reachable opponent needs a contact strike, even while retreating.
        // Approach preferences must not shorten the actual weapon's contact range.
        return distance<=weapon.reach()?MeleeAttack.QUICK:MeleeAttack.LUNGE;
    }
    public static MeleeAttack attack(CombatState state,CombatOrder order,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId observer,long incarnation,long tick,
            WeaponProfile weapon,double distance,double separatingSpeed,MeleeRules rules){
        return attack(state,order,observer,incarnation,tick,weapon,distance,separatingSpeed,0,rules);
    }
    public static MeleeAttack attack(CombatState state,CombatOrder order,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId observer,long incarnation,long tick,
            WeaponProfile weapon,double distance,double separatingSpeed,double lateralSpeed,MeleeRules rules){
        if(!Double.isFinite(lateralSpeed) || lateralSpeed<0)throw new IllegalArgumentException("invalid observed lateral speed");
        return CombatTechniqueSelection.choose(state,order,observer,incarnation,tick,weapon,distance,separatingSpeed,lateralSpeed,rules);
    }
    public static boolean opportunity(WeaponProfile weapon,MeleeAttack attack,double distance,MeleeRules rules){
        return Double.isFinite(distance) && distance>=0 && distance<=CombatMechanics.reach(weapon,attack)+rules.techniques().require(attack).approachAllowance();
    }
    public static Decision decide(CombatOrder order,Facts facts){
        if(!order.current(facts.tick()) || !facts.targetAlive())return Decision.FINISHED;
        if(facts.pending() || !facts.usableWeapon())return Decision.WAIT;
        if(!facts.visible() || !facts.inReach())return Decision.APPROACH;
        return facts.tick()<facts.readyAt()?Decision.WAIT:Decision.ATTACK;
    }
    public static MeleeSector sector(CombatState state,CombatOrder order,long tick,MeleeRules rules){
        var plan=state.engagements().get(CombatEngagement.Key.of(order));
        // Admission preview uses the same allocator as the later canonical enlistment.
        if(plan==null)plan=CombatEngagement.first(order,tick);
        else if(!plan.participants().containsKey(order.owner()) || !plan.participants().get(order.owner()).execution().equals(order.execution()))plan=plan.admitted(order,tick);
        return plan.sector(order.execution());
    }
    public static boolean readyAt(CombatState state,CombatOrder order,SurfaceAnchor actual,long tick,MeleeRules rules){
        var sector=sector(state,order,tick,rules);
        return (sector.role()==MeleeSector.Role.PRESSURE || sector.role()==MeleeSector.Role.SUPPORT)
                || sector.alignment(actual,order.targetPosition())>=rules.engagements().flankAlignmentPermille()/1000.0;
    }
    private MeleeTactics(){}
}
