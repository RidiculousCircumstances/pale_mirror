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
        return distance<=Math.min(rules.techniques().quickDistanceMicros()/1_000_000.0,weapon.reach()*rules.techniques().quickReachPermille()/1000.0)
                && separatingSpeed<rules.techniques().retreatSpeedMicros()/1_000_000.0?MeleeAttack.QUICK:MeleeAttack.LUNGE;
    }
    public static boolean opportunity(WeaponProfile weapon,MeleeAttack attack,double distance,MeleeRules rules){
        return Double.isFinite(distance) && distance>=0 && distance<=weapon.reach()+rules.techniques().require(attack).approachAllowance();
    }
    public static Decision decide(CombatOrder order,Facts facts){
        if(!order.current(facts.tick()) || !facts.targetAlive())return Decision.FINISHED;
        if(facts.pending() || !facts.usableWeapon())return Decision.WAIT;
        if(!facts.visible() || !facts.inReach())return Decision.APPROACH;
        return facts.tick()<facts.readyAt()?Decision.WAIT:Decision.ATTACK;
    }
    public static MeleeSector sector(CombatState state,CombatOrder order,long tick,MeleeRules rules){
        var retained=state.positions().get(order.owner());
        if(retained!=null && retained.execution().equals(order.execution()) && retained.target().equals(order.target())
                && retained.active(tick,rules))return retained.sector();
        var peers=state.positions().entrySet().stream().filter(e->!e.getKey().equals(order.owner()) && e.getValue().target().equals(order.target())
                && e.getValue().active(tick,rules)).sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
        if(peers.isEmpty())return MeleeSector.pressure(order.actorPosition(),order.targetPosition());
        var counts=new EnumMap<MeleeSector.Role,Integer>(MeleeSector.Role.class);for(var peer:peers)counts.merge(peer.sector().role(),1,Integer::sum);
        var role=Arrays.stream(MeleeSector.Role.values()).min(Comparator.comparingInt((MeleeSector.Role r)->counts.getOrDefault(r,0)).thenComparingInt(MeleeSector.Role::wireTag)).orElseThrow();
        var bearing=peers.getFirst().sector();return new MeleeSector(role,bearing.axisX(),bearing.axisZ());
    }
    public static boolean readyAt(CombatState state,CombatOrder order,SurfaceAnchor actual,long tick,MeleeRules rules){
        var point=state.positions().get(order.owner());var sector=sector(state,order,tick,rules);
        return sector.role()==MeleeSector.Role.PRESSURE || point!=null && point.execution().equals(order.execution())
                && point.target().equals(order.target()) && point.station().filter(p->p.manhattanDistance(actual)<=1).isPresent()
                || sector.alignment(actual,order.targetPosition())>=rules.alignmentPermille()/1000.0;
    }
    private MeleeTactics(){}
}
