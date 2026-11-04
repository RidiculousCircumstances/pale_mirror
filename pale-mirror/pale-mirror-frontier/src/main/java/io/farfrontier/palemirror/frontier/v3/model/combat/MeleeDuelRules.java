package io.farfrontier.palemirror.frontier.v3.model.combat;

/** Hashed local tactical policy; no role, world geometry or native actuation. */
public record MeleeDuelRules(int pressureWindowTicks, int repeatedHits, int repeatedMisses,
        int lateralDistance, int lateralCooldown, int sharedCooldown, int preparation,
        int travelTicks, int recovery, int pacePermille, int maximumGapMicros) {
    public MeleeDuelRules {
        if (pressureWindowTicks < 1 || pressureWindowTicks > 400 || repeatedHits < 2 || repeatedHits > 8
                || repeatedMisses < 2 || repeatedMisses > 8 || lateralDistance < 1 || lateralDistance > 3
                || sharedCooldown < 1 || lateralCooldown < sharedCooldown || lateralCooldown > 400
                || preparation < 1 || preparation > 10 || travelTicks < 1 || travelTicks > 40
                || recovery < 1 || recovery > sharedCooldown || pacePermille < 1000 || pacePermille > 3000
                || maximumGapMicros < 1 || maximumGapMicros > 8_000_000)
            throw new IllegalArgumentException("invalid melee duel policy");
    }
    public static MeleeDuelRules initial() {
        return new MeleeDuelRules(80,2,2,2,24,12,1,20,2,2600,4_500_000);
    }
    public record StepShape(int forwardMicros,int lateralMicros,int minimumSideMicros,int minimumForwardMicros,
            int maximumForwardMicros,int minimumGapMicros,int radialSlackMicros) {
        public StepShape {
            if(forwardMicros<0 || lateralMicros<1 || minimumSideMicros<1 || minimumForwardMicros>maximumForwardMicros
                    || minimumGapMicros<1 || radialSlackMicros<0)throw new IllegalArgumentException("invalid lateral step shape");
        }
        String canonicalText(){return forwardMicros+":"+lateralMicros+":"+minimumSideMicros+":"+minimumForwardMicros
                +":"+maximumForwardMicros+":"+minimumGapMicros+":"+radialSlackMicros;}
    }
    public StepShape shape(){return new StepShape(400_000,1_500_000,600_000,-1_000_000,1_250_000,1_000_000,1_000_000);}
    public String canonicalText() {
        return pressureWindowTicks+":"+repeatedHits+":"+repeatedMisses+":"+lateralDistance+":"+lateralCooldown
                +":"+sharedCooldown+":"+preparation+":"+travelTicks+":"+recovery+":"+pacePermille+":"+maximumGapMicros+"|shape="+shape().canonicalText();
    }
}
