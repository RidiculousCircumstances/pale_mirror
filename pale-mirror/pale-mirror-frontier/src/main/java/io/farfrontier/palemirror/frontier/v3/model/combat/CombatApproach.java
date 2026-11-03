package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

/** Tactical preferences over real geometry candidates; this is not a second route finder. */
public final class CombatApproach {
    public static boolean arc(CombatOrder order,MeleeSector sector,EngagementRules rules){
        return sector.role()!=MeleeSector.Role.PRESSURE && sector.role()!=MeleeSector.Role.SUPPORT
                && sector.alignment(order.actorPosition(),order.targetPosition())<rules.flankAlignmentPermille()/1000.0;
    }
    public static double score(CombatOrder order,MeleeSector sector,SurfaceAnchor candidate,EngagementRules rules){
        double desired=Math.atan2(sector.axisZ(),sector.axisX())+switch(sector.role()){
            case PRESSURE,SUPPORT->0;case FLANK_LEFT->Math.PI/2;case FLANK_RIGHT->-Math.PI/2;case CUT_OFF->Math.PI;};
        double angle=Math.atan2((double)order.actorPosition().z()-order.targetPosition().z(),(double)order.actorPosition().x()-order.targetPosition().x());
        double delta=Math.atan2(Math.sin(desired-angle),Math.cos(desired-angle));
        // The two flank directions are stable; a half-turn cut-off uses the currently shorter side.
        if(sector.role()==MeleeSector.Role.FLANK_LEFT && delta<0)delta+=2*Math.PI;
        if(sector.role()==MeleeSector.Role.FLANK_RIGHT && delta>0)delta-=2*Math.PI;
        double next=angle+Math.copySign(Math.min(Math.abs(delta),Math.toRadians(rules.arcDegrees())),delta);
        double x=order.targetPosition().x()+Math.cos(next)*rules.outerRadius();
        double z=order.targetPosition().z()+Math.sin(next)*rules.outerRadius();
        return rules.arcWeightPermille()/1000.0*Math.hypot(candidate.x()-x,candidate.z()-z)+0.05*candidate.manhattanDistance(order.actorPosition());
    }
    private CombatApproach() { }
}
