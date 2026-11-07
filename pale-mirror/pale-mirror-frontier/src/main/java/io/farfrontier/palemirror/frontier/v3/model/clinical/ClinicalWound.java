package io.farfrontier.palemirror.frontier.v3.model.clinical;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Treatment accounting over the last observed vitality, never a competing current-health authority. */
public record ClinicalWound(SubjectId actor,long observedHealth,long maximumHealth,long untreated,long dressed,
        int qualityPermille,long harmRevision,long evaluatedAt){
    public ClinicalWound {
        Objects.requireNonNull(actor);
        if(observedHealth<0||maximumHealth<1||observedHealth>maximumHealth||untreated<0||dressed<0
                ||untreated+dressed>maximumHealth-observedHealth||qualityPermille<0||qualityPermille>1000
                ||harmRevision<0||evaluatedAt<0||dressed==0&&qualityPermille!=0)throw new IllegalArgumentException("invalid wound accounting");
    }
    public static ClinicalWound observed(SubjectId actor,FixedScalar health,FixedScalar maximum,long tick){
        return new ClinicalWound(actor,health.raw(),maximum.raw(),maximum.raw()-health.raw(),0,0,1,tick);
    }
    public ClinicalWound observe(FixedScalar health,long tick){
        if(tick<evaluatedAt||health.raw()>maximumHealth)throw new IllegalArgumentException("stale or unsupported vitality observation");
        long change=health.raw()-observedHealth;
        if(change<0)return new ClinicalWound(actor,health.raw(),maximumHealth,Math.addExact(untreated,-change),dressed,qualityPermille,harmRevision+1,tick);
        long remainingDressed=Math.max(0,dressed-change),remainingUntreated=Math.max(0,untreated-Math.max(0,change-dressed));
        return new ClinicalWound(actor,health.raw(),maximumHealth,remainingUntreated,remainingDressed,remainingDressed==0?0:qualityPermille,harmRevision,tick);
    }
    public boolean needsDressing(int quality){return untreated>0||dressed>0&&quality>=qualityPermille+100;}
    public ClinicalWound dressed(int quality,long coverage,long expectedHarmRevision,long tick){
        if(quality<1||quality>1000||coverage<1||expectedHarmRevision!=harmRevision||tick<evaluatedAt||!needsDressing(quality))
            throw new IllegalArgumentException("stale or ineffective dressing");
        long fresh=Math.min(untreated,coverage),improved=Math.min(dressed,coverage-fresh),total=dressed+fresh;
        long weighted=(long)qualityPermille*(dressed-improved)+(long)quality*(fresh+improved);
        return new ClinicalWound(actor,observedHealth,maximumHealth,untreated-fresh,total,(int)(weighted/total),harmRevision,tick);
    }
    public long recoveryUnits(long throughTick,long restUnits,ClinicalRules rules){
        if(throughTick<evaluatedAt||restUnits<rules.naturalUnitsPerTick())throw new IllegalArgumentException("invalid recovery interval");
        long deficit=maximumHealth-observedHealth;
        long ticks=Math.min(throughTick-evaluatedAt,Math.ceilDiv(deficit,restUnits));
        long bonus=rules.tendedUnitsPerTick()*qualityPermille/1000;
        long fastRate=Math.addExact(restUnits,bonus);
        long full=Math.min(ticks,dressed/fastRate);
        long extra=Math.multiplyExact(full,bonus);
        if(full<ticks)extra=Math.addExact(extra,Math.min(bonus,Math.max(0,dressed-full*fastRate-restUnits)));
        return Math.min(deficit,Math.addExact(Math.multiplyExact(ticks,restUnits),extra));
    }
}
