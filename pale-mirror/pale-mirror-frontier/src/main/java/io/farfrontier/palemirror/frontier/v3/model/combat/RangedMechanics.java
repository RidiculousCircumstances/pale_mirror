package io.farfrontier.palemirror.frontier.v3.model.combat;
/** Permanent actor preparation modifies execution, never ammunition quantity or vanilla collision damage. */
public final class RangedMechanics {
    private static int factor(int level,RangedRules rules){return 1000+rules.trainingVariationPermille()*(50-level)/50;}
    public static int reloadTicks(CombatProfile profile,RangedRules rules){return Math.max(1,rules.reloadTicks()*factor(profile.attributes().agility(),rules)/1000);}
    public static int aimTicks(CombatProfile profile,RangedRules rules){return Math.max(1,rules.aimTicks()*factor(profile.skill(CombatSkillId.CROSSBOW),rules)/1000);}
    public static float inaccuracy(CombatProfile profile,RangedRules rules){return rules.inaccuracyMicros()/1000000f*factor((profile.skill(CombatSkillId.CROSSBOW)+profile.attributes().perception())/2,rules)/1000f;}
    private RangedMechanics(){}
}
