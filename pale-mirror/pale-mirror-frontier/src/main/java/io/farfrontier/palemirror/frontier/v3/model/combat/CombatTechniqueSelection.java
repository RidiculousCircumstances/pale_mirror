package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;
/** Common learned repertoire. Skills admit; permanent attributes execute; style ranks opportunities. */
public final class CombatTechniqueSelection {
    public static MeleeAttack choose(CombatState state,CombatOrder order,ActorActuationId actor,long incarnation,long tick,
            WeaponProfile weapon,double distance,double separating,double lateral,MeleeRules rules){
        var next=state.followups().get(order.owner());
        if(next!=null && next.matches(order,actor,incarnation,tick) && CombatMechanics.unlocked(order.profile(),weapon,next.next())
                && MeleeTactics.opportunity(weapon,next.next(),distance,rules))return next.next();
        var stance=state.stances().get(order.owner());var observed=stance!=null && stance.current(order,tick,rules)?stance.threat():CombatThreatObservation.none();
        var prior=state.maneuvers().get(order.owner());boolean evaded=prior!=null && prior.matches(order) && !prior.open() && !prior.kind().closing()
            && prior.kind()!=MeleeManeuver.Kind.BLOCK && tick>=prior.closedAt() && tick-prior.closedAt()<=rules.training().number("observation.counterTicks");
        var options=new ArrayList<Weighted>();
        for(var t:rules.training().techniques()){
            if(!CombatMechanics.unlocked(order.profile(),weapon,t.id()) || !MeleeTactics.opportunity(weapon,t.id(),distance,rules)
                    || !state.affords(order.owner(),tick,CombatMechanics.cost(order.profile(),rules.readiness().cost(t.id())),rules))continue;
            if(separating>0.1 && !t.id().guidesApproach())continue;
            boolean legal=switch(t.use()){
                case CONTACT->distance<=weapon.reach();
                case CLOSING->distance>Math.min(2.0,weapon.reach()-0.5) && separating>=0;
                case LATERAL_TARGET->lateral>=rules.techniques().sweepSpeedMicros()/1_000_000.0 && separating<=0.1;
                case CROWDED->distance<=CombatMechanics.reach(weapon,t.id());
                case LATERAL_STEP,FLANK_STEP,RETREAT_STEP->distance<=weapon.reach();
                case GROUP->observed.opponents()>1;
                case COUNTER->observed.counter(tick,rules);
                case EVADE_COUNTER->evaded && order.profile().knows(CombatSkillId.EVASION,rules.training().number("defence.counter"));
                case CONTINUATION->false;
            };
            if(!legal)continue;
            int weight=switch(t.use()){
                case LATERAL_STEP,FLANK_STEP,EVADE_COUNTER->Math.max(1,order.style().lateralWeight());
                case RETREAT_STEP->Math.max(1,order.style().retreatWeight());
                case CLOSING->Math.max(1,order.style().burstWeight());
                case COUNTER->order.style().attackWeight()*2;
                case GROUP->order.style().attackWeight()*3;
                default->order.style().attackWeight();
            };
            if(t.timing().preparationTicks()>6)weight=Math.max(1,weight*(100-order.style().effortReserve())/100);
            options.add(new Weighted(t.id(),weight));
        }
        if(options.isEmpty())return distance>weapon.reach() && CombatMechanics.unlocked(order.profile(),weapon,MeleeAttack.LUNGE)?MeleeAttack.LUNGE:MeleeAttack.BASIC_CUT;
        long salt=order.execution().actorId().value().hashCode()^state.nextAttack().getOrDefault(order.owner(),0L);
        salt=(salt^(salt>>>30))*0xbf58476d1ce4e5b9L;salt=(salt^(salt>>>27))*0x94d049bb133111ebL;salt^=salt>>>31;
        int pick=(int)Math.floorMod(salt,options.stream().mapToInt(Weighted::weight).sum());
        for(var option:options){pick-=option.weight();if(pick<0)return option.id();}
        throw new IllegalStateException("technique weight accounting");
    }
    private record Weighted(MeleeAttack id,int weight){}
    private CombatTechniqueSelection(){}
}
