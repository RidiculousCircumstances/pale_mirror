package io.farfrontier.palemirror.frontier.v3.model.combat;
/** Sole permanent-stat calibration. Consumers must not apply attribute multipliers again. */
public final class CombatMechanics {
    public static boolean unlocked(CombatProfile profile,WeaponProfile weapon,MeleeAttack attack){
        return profile.knows(weapon.skill(),CombatTrainingCatalog.standard().require(attack).minimum());
    }
    public static int interpolate(int score,int low,int high){return low+(high-low)*score/100;}
    public static int speed(CombatProfile profile){var c=CombatTrainingCatalog.standard();return interpolate(profile.attributes().agility(),c.number("attributes.speedMin"),c.number("attributes.speedMax"));}
    public static int duration(int ticks,CombatProfile profile){return Math.max(1,(ticks*1000+speed(profile)-1)/speed(profile));}
    public static MeleeTechniques.Technique timing(CombatProfile profile,MeleeAttack attack,MeleeRules rules){
        var base=rules.techniques().require(attack);return new MeleeTechniques.Technique(duration((int)rules.windup(attack),profile),base.activeTicks(),duration(base.recoveryTicks(),profile),base.approachMicros());
    }
    public static int cost(CombatProfile profile,int base){var c=CombatTrainingCatalog.standard();return Math.max(1,base*interpolate(profile.attributes().endurance(),c.number("attributes.effortMax"),c.number("attributes.effortMin"))/1000);}
    public static double damage(CombatProfile profile,WeaponProfile weapon,MeleeAttack attack){
        var c=CombatTrainingCatalog.standard();var t=c.require(attack);
        int physical=interpolate(profile.attributes().strength(),c.number("attributes.damageMin"),c.number("attributes.damageMax"));
        int learned=interpolate(profile.skill(weapon.skill()),c.number("attributes.skillDamageMin"),c.number("attributes.skillDamageMax"));
        return weapon.damage()*t.damagePermille()/1000.0*physical/1000.0*learned/1000.0;
    }
    public static double reach(WeaponProfile weapon,MeleeAttack attack){return attack==MeleeAttack.KICK?Math.min(1.35,weapon.reach()):weapon.reach();}
    public static double knockback(CombatProfile profile,MeleeAttack attack){var c=CombatTrainingCatalog.standard();return c.require(attack).knockbackPermille()/1000.0
        *interpolate(profile.attributes().strength(),c.number("attributes.knockbackMin"),c.number("attributes.knockbackMax"))/1000.0;}
    public static int reactionTicks(CombatProfile profile){var c=CombatTrainingCatalog.standard();return interpolate((profile.attributes().perception()+Math.max(profile.skill(CombatSkillId.EVASION),profile.skill(CombatSkillId.WEAPON_DEFENCE)))/2,
        c.number("observation.reactionMax"),c.number("observation.reactionMin"));}
    public static boolean permits(CombatProfile profile,MeleeManeuver.Kind kind){var c=CombatTrainingCatalog.standard();return switch(kind){
        case BURST,PURSUIT_RUN->true;
        case BACKSTEP->profile.knows(CombatSkillId.EVASION,c.number("defence.backstep"));
        case SIDESTEP_LEFT,SIDESTEP_RIGHT->profile.knows(CombatSkillId.EVASION,c.number("defence.sidestep"));
        case DIAGONAL_LEFT,DIAGONAL_RIGHT->profile.knows(CombatSkillId.EVASION,c.number("defence.diagonal"));
        case ADVANCE_LEFT,ADVANCE_RIGHT->profile.knows(CombatSkillId.EVASION,c.number("defence.advance"));
        case BLOCK->profile.knows(CombatSkillId.WEAPON_DEFENCE,c.number("defence.block"));
    };}
    public static double defensivePace(CombatProfile profile,MeleeManeuver.Kind kind){return kind.closing()?1:speed(profile)/1000.0;}
    public static int blockReduction(CombatProfile profile){return CombatTrainingCatalog.standard().number("defence.blockReduction")
        *interpolate(profile.skill(CombatSkillId.WEAPON_DEFENCE),750,1000)/1000;}
    private CombatMechanics(){}
}
