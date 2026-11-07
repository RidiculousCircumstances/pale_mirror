package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.process.ClinicalProgressObligation;

/** One resident's actual care/rest state; no planning, forced loading or mutation. */
final class FrontierV3ClinicalDiagnostic {
    static String fields(FrontierWorldState s,SubjectId actor){
        var task=s.humanPopulation().clinical().taskFor(actor);var wound=s.humanPopulation().clinical().wounds().get(actor);
        var rest=s.humanPopulation().restPlaces().uses().get(actor);
        return ",\"clinical\":{\"healthRaw\":"+s.actorLocations().get(actor).condition().health().raw()
                +",\"medicineSkill\":"+s.humanPopulation().resident(actor).skill(ResidentSkill.MEDICINE)
                +",\"task\":"+string(task.map(t->t.id().value()).orElse(""))
                +",\"phase\":"+string(task.map(t->t.phase().name()).orElse("NONE"))
                +",\"role\":"+string(task.map(t->t.role().name()).orElse("NONE"))
                +",\"patient\":"+string(task.map(t->t.patient().value()).orElse(""))
                +",\"untreatedRaw\":"+(wound==null?0:wound.untreated())+",\"dressedRaw\":"+(wound==null?0:wound.dressed())
                +",\"qualityPermille\":"+(wound==null?0:wound.qualityPermille())
                +",\"dressed\":"+(wound!=null&&wound.dressed()>0)
                +",\"careComplete\":"+(task.isEmpty()&&rest==null&&s.actorLocations().get(actor).condition().health().raw()>=ActorCondition.HEALTHY.health().raw()*s.bootstrap().ruleset().clinical().finishRestPermille()/1000)
                +",\"healingPending\":"+s.humanPopulation().clinical().healing().containsKey(actor)
                +",\"restPhase\":"+string(rest==null?"NONE":rest.phase().name())
                +",\"bedBuilding\":"+string(rest==null?"":rest.place().id().buildingId().value())
                +",\"bedId\":"+string(rest==null?"":rest.place().id().localId())
                +",\"bedsidePosition\":"+FrontierV3DiagnosticJson.position(rest==null?s.actorLocations().get(actor).body():rest.approach().standingBody())
                +",\"bedRecoveryPermille\":"+(rest==null?0:rest.place().profile().recoveryPermille())+"}";
    }
    static String obligation(FrontierWorldState s,SubjectId actor){
        var o=ClinicalProgressObligation.describe(s,actor);
        return ",\"progressObligation\":{\"schema\":1,\"rule\":\"frontier.clinical-care.progress.v1\",\"subject\":"+string(actor.value())
                +",\"worker\":"+string(actor.value())+",\"generation\":1,\"revision\":"+o.revision()
                +",\"fingerprint\":"+string(o.fingerprint())+",\"budgetTicks\":"+o.budgetTicks()
                +",\"disposition\":"+string(o.disposition())+",\"next\":"+string(o.next())+"}";
    }
    private static String string(String value){return "\""+FrontierV3DiagnosticJson.quote(value)+"\"";}
    private FrontierV3ClinicalDiagnostic(){}
}
