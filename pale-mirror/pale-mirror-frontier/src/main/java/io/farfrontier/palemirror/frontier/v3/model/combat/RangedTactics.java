package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;

/** Reusable bounded ranged policy. Role, body actuation, ammunition and damage retain their existing owners. */
public final class RangedTactics {
    public static int preferredRange(CombatOrder order,RangedRules rules){
        return Math.clamp(rules.preferredRange()+(order.style().preferredGapMicros()-2200000)/200000,rules.minimumRange()+2,rules.maximumRange()-3);
    }
    public static Optional<RangedFollowup> followup(RangedState state,CombatOrder order,long tick){
        return Optional.ofNullable(state.followups().get(order.owner())).filter(v->v.current(order,tick));
    }
    public static boolean relocate(RangedState state,CombatOrder order,long tick){
        return followup(state,order,tick).filter(v->!v.moved(order.actorPosition()))
                .filter(v->v.technique()==RangedTechnique.REPOSITIONING_SEQUENCE
                    || order.profile().knows(CombatSkillId.CROSSBOW,45)&&order.style().lateralWeight()>order.style().attackWeight()
                        &&v.technique()!=RangedTechnique.AFTER_MANEUVER).isPresent();
    }
    public static boolean relocationStation(RangedFollowup last,CombatOrder order,SurfaceAnchor point){
        if(!last.moved(point))return false;
        // The top-tier sequence first withdraws from the enemy; no forced dodge or imaginary passage.
        return last.technique()!=RangedTechnique.REPOSITIONING_SEQUENCE
                || RangedPositions.distance(point,order.targetPosition())>=RangedPositions.distance(last.origin(),order.targetPosition())+2;
    }
    public static RangedTechnique choose(RangedState state,CombatOrder order,RangedStance sight,long tick,RangedRules rules){
        var last=followup(state,order,tick);var style=order.style();
        // The second shot in the sequence is ordinary and performs its complete reload.
        if(last.filter(v->v.technique()==RangedTechnique.REPOSITIONING_SEQUENCE&&v.moved(order.actorPosition())).isPresent())return RangedTechnique.ORDINARY;
        var scores=new EnumMap<RangedTechnique,Integer>(RangedTechnique.class);scores.put(RangedTechnique.ORDINARY,40);
        if(sight.approaching())scores.put(RangedTechnique.HURRIED,80+style.attackWeight());
        if(!sight.moving())scores.put(RangedTechnique.PRECISE,50+style.retreatWeight());
        if(last.filter(v->v.moved(order.actorPosition())).isPresent())scores.put(RangedTechnique.AFTER_MANEUVER,90+style.lateralWeight());
        if(!sight.approaching())scores.put(RangedTechnique.HELD,30+style.retreatWeight());
        if(sight.attacking()||sight.openingAt()>=0&&tick-sight.openingAt()<=8)scores.put(RangedTechnique.COUNTERSHOT,100+style.attackWeight());
        if(RangedPositions.distance(order.actorPosition(),order.targetPosition())<=rules.maximumRange()-3&&last.filter(v->v.technique()==RangedTechnique.AFTER_MANEUVER).isEmpty())
            scores.put(RangedTechnique.REPOSITIONING_SEQUENCE,20+style.lateralWeight());
        return scores.entrySet().stream().filter(e->e.getKey().unlocked(order.profile()))
                .max(Comparator.<Map.Entry<RangedTechnique,Integer>>comparingInt(Map.Entry::getValue).thenComparingInt(e->-e.getKey().tag())).orElseThrow().getKey();
    }
    private RangedTactics(){}
}
