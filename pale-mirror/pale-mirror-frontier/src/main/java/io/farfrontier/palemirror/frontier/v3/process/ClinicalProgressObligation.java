package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.clinical.*;

/** Read-only progress of the named resident's care; no route search or recovery side effect. */
public record ClinicalProgressObligation(long revision,String fingerprint,long budgetTicks,String disposition,String next) {
    public static ClinicalProgressObligation describe(FrontierWorldState s,SubjectId actor){
        var authority=s.actorExecutions().actors().get(actor);var clinical=s.humanPopulation().clinical();
        var task=clinical.taskFor(actor);var use=s.humanPopulation().restPlaces().uses().get(actor);
        var wound=clinical.wounds().get(actor);var rules=s.bootstrap().ruleset().clinical();
        boolean pending=clinical.healing().containsKey(actor)||task.filter(ClinicalTask::pending).isPresent()||use!=null&&use.pendingBody().isPresent();
        boolean closed=task.isEmpty()&&use==null&&!pending;
        boolean interrupted=task.isPresent()&&!ClinicalAuthority.current(s,task.orElseThrow());
        boolean noMedicine=task.isPresent()&&wound!=null&&wound.needsDressing(ClinicalAuthority.quality(s,task.orElseThrow()))
                &&ActorConsumables.available(s,actor,task.orElseThrow().home(),rules.bandage())==0
                &&!ActorSupplyVisits.stockPresent(s,actor,task.orElseThrow().home(),rules.bandage())&&!ClinicalAuthority.wantsRest(s,actor);
        String disposition=closed?"TERMINAL":interrupted?"HIGHER_PRIORITY_ACTIVITY":noMedicine?"SERVICE_ACCESS":"ELIGIBLE";
        String next=closed?"CLOSED":pending?"PHYSICAL_RECEIPT":task.filter(t->t.phase()==ClinicalTask.Phase.TEND).isPresent()?"DRESSING":"CARE_OR_REST";
        long budget=Math.addExact(ActorMovementProcess.maximumJourneyTicks(),Math.addExact(
                Math.ceilDiv(ActorCondition.HEALTHY.health().raw(),rules.naturalUnitsPerTick()),rules.baseProcedureTicks()*2L+rules.reviewTicks()*2L));
        String fingerprint=s.actorLocations().get(actor).condition().health().raw()+":"+task.map(t->t.execution().generation()+":"+t.phase()+":"+t.revision()).orElse("CLOSED")
                +":"+(use==null?"NONE":use.phase()+":"+use.revision())+":"+pending;
        return new ClinicalProgressObligation(authority==null?1:Math.max(1,authority.generation()),fingerprint,budget,disposition,next);
    }
}
