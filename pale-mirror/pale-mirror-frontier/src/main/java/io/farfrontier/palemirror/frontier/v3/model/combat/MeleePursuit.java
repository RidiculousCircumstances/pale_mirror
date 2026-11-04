package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;

/** Scores real candidate surfaces. Observed velocity guides preference, never invents a route. */
public final class MeleePursuit {
    public record Lead(double x,double z) { }
    public static Lead lead(MeleeManeuvers.Facts facts,PursuitRules rules){
        double speed=Math.hypot(facts.targetVelocityX(),facts.targetVelocityZ());
        double scale=speed==0?0:Math.min(rules.leadTicks(),Math.min(rules.maximumVelocityMicros()/1_000_000.0/speed*rules.leadTicks(),
                rules.maximumLeadMicros()/1_000_000.0/speed));
        return new Lead(facts.targetVelocityX()*scale,facts.targetVelocityZ()*scale);
    }
    public static List<SurfaceAnchor> rank(CombatState combat,CombatOrder order,List<SurfaceAnchor> candidates,
            MeleeManeuvers.Facts facts,long tick,MeleeRules rules){
        var origin=order.actorPosition();var target=order.targetPosition();var policy=rules.maneuvers().pursuit();
        var sector=MeleeTactics.sector(combat,order,tick,rules);boolean flank=CombatApproach.arc(order,sector,rules.engagements());
        var lead=lead(facts,policy);double old=Math.hypot(origin.x()-target.x(),origin.z()-target.z());
        return candidates.stream().filter(p->!p.equals(origin) && order.engagement().contains(p)
                && Math.abs(p.x()-origin.x())+Math.abs(p.z()-origin.z())<=policy.distance()
                && p.y()<=origin.y()+policy.distance()*order.navigation().stepUpBlocks()
                && p.y()>=origin.y()-policy.distance()*order.navigation().dropBlocks()
                && Math.hypot(p.x()-target.x(),p.z()-target.z())>=1.25
                && (flank?CombatApproach.score(order,sector,p,rules.engagements())<CombatApproach.score(order,sector,origin,rules.engagements())
                    :Math.hypot(p.x()-target.x(),p.z()-target.z())<old-0.5))
            .distinct().sorted(Comparator.comparingDouble((SurfaceAnchor p)->
                    (flank?CombatApproach.score(order,sector,p,rules.engagements()):0)
                    +Math.hypot(p.x()-target.x()-lead.x(),p.z()-target.z()-lead.z())
                    +0.05*p.manhattanDistance(origin))
                    .thenComparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::z).thenComparingInt(SurfaceAnchor::y)).toList();
    }
    private MeleePursuit() { }
}
