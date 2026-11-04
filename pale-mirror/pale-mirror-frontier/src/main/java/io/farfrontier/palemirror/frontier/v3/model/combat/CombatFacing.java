package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.Optional;
/** Observed horizontal look direction; absent when not seen or looking vertically. */
public record CombatFacing(int x,int z) {
    public CombatFacing {if(x==0 && z==0 || Math.abs((long)x)>1000 || Math.abs((long)z)>1000)throw new IllegalArgumentException("invalid observed combat facing");}
    public static Optional<CombatFacing> observed(double x,double z){
        if(!Double.isFinite(x) || !Double.isFinite(z))throw new IllegalArgumentException("invalid observed facing");
        double length=Math.hypot(x,z);return length<0.000001?Optional.empty():Optional.of(new CombatFacing((int)Math.round(x/length*1000),(int)Math.round(z/length*1000)));
    }
    public double alignment(SurfaceAnchor station,SurfaceAnchor target){
        double dx=(double)station.x()-target.x(),dz=(double)station.z()-target.z(),length=Math.hypot(dx,dz)*Math.hypot(x,z);
        return length==0?0:(dx*x+dz*z)/length;
    }
}
