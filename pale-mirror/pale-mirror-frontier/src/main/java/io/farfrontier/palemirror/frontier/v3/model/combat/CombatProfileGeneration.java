package io.farfrontier.palemirror.frontier.v3.model.combat;
import java.util.*;
/** Only the actor's creation owner calls this policy; inspection/reassignment never rerolls it. */
public final class CombatProfileGeneration {
    public static CombatProfile create(long seed,long declaredEntropy){
        var data=CombatTrainingCatalog.standard();long value=mix(seed^declaredEntropy);
        int band=(int)Math.floorMod(value,100);int baseline=data.generationBaseline(band);
        int sword=score(value,baseline,0),evade=score(value,baseline,1),defend=score(value,baseline,2);
        return new CombatProfile(new CombatAttributes(score(value,data.number("generation.attributeBaseline"),3),score(value,data.number("generation.attributeBaseline"),4),score(value,
            data.number("generation.attributeBaseline"),5),score(value,data.number("generation.attributeBaseline"),6),score(value,data.number("generation.attributeBaseline"),7)),
            Map.of(CombatSkillId.CROSSBOW,score(mix(value),baseline,0),CombatSkillId.SWORD,sword,CombatSkillId.EVASION,evade,CombatSkillId.WEAPON_DEFENCE,defend));
    }
    private static int score(long seed,int baseline,int salt){int spread=salt<3?CombatTrainingCatalog.standard().generationSpread():CombatTrainingCatalog.standard().number("generation.attributeSpread");
        return (int)Math.clamp(baseline+Math.floorMod(mix(seed+salt*0x9e3779b97f4a7c15L),2*spread+1)-spread,0,100);}
    private static long mix(long v){v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;v=(v^(v>>>27))*0x94d049bb133111ebL;return v^(v>>>31);}
    private CombatProfileGeneration(){}
}
