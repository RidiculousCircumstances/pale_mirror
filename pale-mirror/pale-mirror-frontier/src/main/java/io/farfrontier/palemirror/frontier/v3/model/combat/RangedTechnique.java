package io.farfrontier.palemirror.frontier.v3.model.combat;

/** Learned choices, not damage bonuses. Every choice reloads and spends one real arrow. */
public enum RangedTechnique {
    ORDINARY(1,0,1000,1000,0), HURRIED(2,15,500,1600,0),
    PRECISE(3,30,2000,500,0), AFTER_MANEUVER(4,45,750,850,0),
    HELD(5,60,1250,750,24), COUNTERSHOT(6,75,500,1000,24),
    REPOSITIONING_SEQUENCE(7,90,1000,1000,0);

    private final int tag,minimum,aimPermille,spreadPermille,holdTicks;
    RangedTechnique(int tag,int minimum,int aimPermille,int spreadPermille,int holdTicks){
        this.tag=tag;this.minimum=minimum;this.aimPermille=aimPermille;this.spreadPermille=spreadPermille;this.holdTicks=holdTicks;
    }
    public int tag(){return tag;}
    public int minimum(){return minimum;}
    public int aimPermille(){return aimPermille;}
    public int spreadPermille(){return spreadPermille;}
    public int holdTicks(){return holdTicks;}
    public boolean unlocked(CombatProfile profile){return profile.knows(CombatSkillId.CROSSBOW,minimum);}
    public static RangedTechnique require(int tag){
        for(var v:values())if(v.tag==tag)return v;
        throw new IllegalArgumentException("unknown ranged technique "+tag);
    }
    public static String canonicalText(){return java.util.Arrays.stream(values()).map(v->v.name()+":"+v.tag+":"+v.minimum+":"+v.aimPermille+":"+v.spreadPermille+":"+v.holdTicks)
            .collect(java.util.stream.Collectors.joining("|"));}
}
