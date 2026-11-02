package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.Objects;
/** A stable tactical bearing around a target, not a profession or a separate group owner. */
public record MeleeSector(Role role,int axisX,int axisZ) {
    public enum Role {
        PRESSURE(1),FLANK_LEFT(2),FLANK_RIGHT(3),CUT_OFF(4);
        private final int tag;Role(int tag){this.tag=tag;}public int wireTag(){return tag;}
        public static Role require(int tag){for(var value:values())if(value.tag==tag)return value;throw new IllegalArgumentException("unknown melee sector");}
    }
    public MeleeSector {
        Objects.requireNonNull(role);
        if(Math.abs((long)axisX)>1000 || Math.abs((long)axisZ)>1000 || axisX==0 && axisZ==0)throw new IllegalArgumentException("invalid tactical bearing");
    }
    public static MeleeSector pressure(SurfaceAnchor actor,SurfaceAnchor target){
        double dx=(double)actor.x()-target.x(),dz=(double)actor.z()-target.z(),length=Math.hypot(dx,dz);
        return length==0?new MeleeSector(Role.PRESSURE,1000,0):new MeleeSector(Role.PRESSURE,(int)Math.round(1000*dx/length),(int)Math.round(1000*dz/length));
    }
    public double alignment(SurfaceAnchor station,SurfaceAnchor target){
        double dx=(double)station.x()-target.x(),dz=(double)station.z()-target.z();double x=axisX,z=axisZ;
        switch(role){case PRESSURE->{}case FLANK_LEFT->{x=-axisZ;z=axisX;}case FLANK_RIGHT->{x=axisZ;z=-axisX;}case CUT_OFF->{x=-axisX;z=-axisZ;}}
        double size=Math.hypot(dx,dz)*Math.hypot(x,z);return size==0?0:(dx*x+dz*z)/size;
    }
}
