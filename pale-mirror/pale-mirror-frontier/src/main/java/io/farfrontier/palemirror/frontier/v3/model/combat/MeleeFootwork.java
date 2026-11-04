package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;
/** Bounded shared technique footwork, independent of resident/native model or actuator. */
public final class MeleeFootwork {
    public static List<SurfaceAnchor> candidates(CombatState combat,CombatOrder order,MeleeAttack attack,SurfaceAnchor origin,SurfaceAnchor target,long tick,MeleeRules rules){
        if(attack!=MeleeAttack.LATERAL && attack!=MeleeAttack.RETREAT && attack!=MeleeAttack.FLANK)return List.of();
        double length=Math.hypot(target.x()-origin.x(),target.z()-origin.z());if(length<0.01)return List.of();
        double dx=(target.x()-origin.x())/length,dz=(target.z()-origin.z())/length;
        var sector=MeleeTactics.sector(combat,order,tick,rules);var result=new ArrayList<SurfaceAnchor>();
        int preferred=sector.role()==MeleeSector.Role.FLANK_RIGHT?-1:1;
        for(int side:new int[]{preferred,-preferred}){
            boolean retreat=attack==MeleeAttack.RETREAT;double forward=attack==MeleeAttack.FLANK?rules.training().number("footwork.flankForwardMicros")/1_000_000.0:0;
            double x=origin.x()+0.5+(retreat?-dx:dz*side+dx*forward),z=origin.z()+0.5+(retreat?-dz:-dx*side+dz*forward);
            var point=SurfaceAnchor.at((int)Math.floor(x),origin.y(),(int)Math.floor(z));
            if(point.equals(origin) || !order.engagement().contains(point))continue;
            if(sector.role()!=MeleeSector.Role.PRESSURE && sector.role()!=MeleeSector.Role.SUPPORT
                    && sector.alignment(point,target)<Math.min(sector.alignment(origin,target),rules.engagements().flankAlignmentPermille()/1000.0))continue;
            if(!result.contains(point))result.add(point);
        }
        return List.copyOf(result);
    }
    private MeleeFootwork(){}
}
