package io.farfrontier.palemirror.frontier.v3.model.combat;
import java.util.Objects;
/** Hashed world policy, independent of the render keyframes and role adapters. */
public record MeleeTechniques(Technique quick,Technique lunge,int quickDistanceMicros,int quickReachPermille,
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
        Objects.requireNonNull(quick);Objects.requireNonNull(lunge);
        if(quickDistanceMicros<1 || quickDistanceMicros>4_000_000 || quickReachPermille<1 || quickReachPermille>1000
                || retreatSpeedMicros<0 || retreatSpeedMicros>1_000_000 || pressureDistanceMicros<1 || pressureDistanceMicros>4_000_000)
            throw new IllegalArgumentException("invalid attack selection policy");
    }
    public Technique require(MeleeAttack attack){return switch(attack){case QUICK->quick;case LUNGE->lunge;};}
    public static MeleeTechniques initial(){
        return new MeleeTechniques(new Technique(4,3,6,350_000),new Technique(10,4,10,1_100_000),2_200_000,750,80_000,1_650_000);
    }
    public String canonicalText(){return "quick="+quick.canonicalText()+"|lunge="+lunge.canonicalText()+"|choice="+quickDistanceMicros+":"
            +quickReachPermille+":"+retreatSpeedMicros+":"+pressureDistanceMicros;}
}
