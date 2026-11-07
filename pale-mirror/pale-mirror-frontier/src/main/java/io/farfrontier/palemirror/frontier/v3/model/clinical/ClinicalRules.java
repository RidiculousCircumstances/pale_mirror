package io.farfrontier.palemirror.frontier.v3.model.clinical;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;

/** Initial content calibration in canonical ticks. No experience gain or second vitality value. */
public record ClinicalRules(String bandage,int bandagesPerCloth,long coverageUnits,int qualityCapPermille,
        int selfQualityPermille,int baseProcedureTicks,long naturalUnitsPerTick,long tendedUnitsPerTick,int reviewTicks,int supplyLoad,int firstAidPermille,int restBelowPermille,int finishRestPermille){
    public ClinicalRules {
        if(!bandage.matches("[a-z_]+:[a-z_/]+")||bandagesPerCloth<1||bandagesPerCloth>64||coverageUnits<1
                ||qualityCapPermille<1||qualityCapPermille>1000||selfQualityPermille<1||selfQualityPermille>1000
                ||restBelowPermille<1||finishRestPermille<=restBelowPermille||finishRestPermille>1000||firstAidPermille<0||firstAidPermille>1000||baseProcedureTicks<1||naturalUnitsPerTick<1||tendedUnitsPerTick<1||reviewTicks<1||
                    supplyLoad<1||supplyLoad>64)
            throw new IllegalArgumentException("invalid clinical rules");
    }
    public static ClinicalRules initial(){return new ClinicalRules("pale_mirror:bandage",4,4*FixedScalar.SCALE,800,700,160,200,2000,200,4,100,750,950);}
    public int quality(int medicine,boolean self){
        requireSkill(medicine);int value=200+8*medicine;
        return Math.min(qualityCapPermille,self?value*selfQualityPermille/1000:value);
    }
    public int procedureTicks(int medicine){requireSkill(medicine);return Math.max(1,baseProcedureTicks*(150-medicine)/100);}
    private static void requireSkill(int value){if(value<0||value>100)throw new IllegalArgumentException("medicine skill outside0..100");}
    public String canonicalText(){return bandage+":"+bandagesPerCloth+":"+coverageUnits+":"+qualityCapPermille+":"+selfQualityPermille+":"+baseProcedureTicks
            +":"+naturalUnitsPerTick+":"+tendedUnitsPerTick+":"+reviewTicks+":"+supplyLoad+":"+firstAidPermille+":"+restBelowPermille+":"+finishRestPermille;}
}
