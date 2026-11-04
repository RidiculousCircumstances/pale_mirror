package io.farfrontier.palemirror.frontier.v3.model.combat;
import java.util.Objects;
/** Hashed world policy, independent of the render keyframes and role adapters. */
public record MeleeTechniques(Technique quick,Technique lunge,Technique sweep,int sweepDistanceMicros,int sweepSpeedMicros,int sweepHalfAngleDegrees,int quickDistanceMicros,int quickReachPermille,
        int retreatSpeedMicros,int pressureDistanceMicros) {
    public record Technique(int preparationTicks,int activeTicks,int recoveryTicks,int approachMicros){
        public Technique {
            if(preparationTicks<1 || preparationTicks>40 || activeTicks<1 || activeTicks>20
                    || recoveryTicks<1 || recoveryTicks>40 || approachMicros<0 || approachMicros>2_000_000)
                throw new IllegalArgumentException("invalid melee technique policy");
        }
        public double approachAllowance(){return approachMicros/1_000_000.0;}
        String canonicalText(){return preparationTicks+":"+activeTicks+":"+recoveryTicks+":"+approachMicros;}
    }
    public MeleeTechniques {
        Objects.requireNonNull(quick);Objects.requireNonNull(lunge);Objects.requireNonNull(sweep);
        if(sweepDistanceMicros<1 || sweepDistanceMicros>quickDistanceMicros || sweepSpeedMicros<1 || sweepSpeedMicros>1_000_000
                || sweepHalfAngleDegrees<45 || sweepHalfAngleDegrees>100)throw new IllegalArgumentException("invalid sweep policy");
        if(quickDistanceMicros<1 || quickDistanceMicros>4_000_000 || quickReachPermille<1 || quickReachPermille>1000
                || retreatSpeedMicros<0 || retreatSpeedMicros>1_000_000 || pressureDistanceMicros<1 || pressureDistanceMicros>4_000_000)
            throw new IllegalArgumentException("invalid attack selection policy");
    }
    public Technique require(MeleeAttack attack){
        return switch(attack){case QUICK->quick;case LUNGE->lunge;case SWEEP->sweep;case FOLLOWUP->MeleeFollowupRules.initial().technique();};}
    public static MeleeTechniques initial(){
        return new MeleeTechniques(new Technique(4,3,6,350_000),new Technique(8,4,14,650_000),new Technique(10,3,16,0),2_200_000,100_000,100,2_200_000,750,80_000,2_200_000);
    }
    public String canonicalText(){
        return "quick="+quick.canonicalText()+"|lunge="+lunge.canonicalText()
            +"|sweep="+sweep.canonicalText()+":"+sweepDistanceMicros+":"+sweepSpeedMicros+":"+sweepHalfAngleDegrees
            +"|choice="+quickDistanceMicros+":"
            +quickReachPermille+":"+retreatSpeedMicros+":"+pressureDistanceMicros;}
}
