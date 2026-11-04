package io.farfrontier.palemirror.frontier.v3.model.combat;

/** Motion measured from observed coordinates. Native entity impulse is not player locomotion. */
public record CombatMotion(double x,double z) {
    public CombatMotion {if(!Double.isFinite(x) || !Double.isFinite(z))throw new IllegalArgumentException("invalid observed combat motion");}
    public static CombatMotion between(double priorX,double priorZ,double currentX,double currentZ,long elapsedTicks){
        if(elapsedTicks<1)throw new IllegalArgumentException("combat motion requires a positive observation interval");
        return new CombatMotion((currentX-priorX)/elapsedTicks,(currentZ-priorZ)/elapsedTicks);
    }
    public double separating(double radialX,double radialZ){return x*radialX+z*radialZ;}
    public double lateral(double radialX,double radialZ){return Math.abs(x*radialZ-z*radialX);}
}
