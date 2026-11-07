package io.farfrontier.palemirror.frontier.v3.model.clinical;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.model.rest.*;
import java.util.*;

/** Medical decisions, not an alternative body, inventory or route owner. */
public final class ClinicalAuthority {
    public static boolean enabled(FrontierWorldState s){return s.bootstrap().ruleset().labour().entries().containsKey(ResidentWorkKind.MEDICAL);}
    public static ClinicalTask require(FrontierWorldState s,ActorExecutionId id){
        var task=s.humanPopulation().clinical().tasks().get(id.activityOwnerId());
        if(id.activityKind()!=ActorActivityKind.CLINICAL_CARE||task==null||!task.execution().equals(id))throw new IllegalArgumentException("no exact clinical execution");
        return task;
    }
    public static ClinicalTask task(FrontierWorldState s,SubjectId id,long revision){
        var task=Objects.requireNonNull(s.humanPopulation().clinical().tasks().get(id),"clinical task");
        if(task.revision()!=revision)throw new IllegalArgumentException("stale clinical revision");return task;
    }
    public static boolean current(FrontierWorldState s,ClinicalTask task){
        var execution=s.actorExecutions().actors().get(task.actor());return execution!=null&&execution.current().equals(Optional.of(task.execution()));
    }
    public static boolean pendingForContainer(FrontierWorldState s,SubjectId container){return s.humanPopulation().clinical().tasks().values().stream().flatMap(t->t.supply().stream())
        .anyMatch(v->v.pending().isPresent()&&v.order() instanceof ActorContainerItemOrder order&&order.containerEndpoint().containerId().equals(container));}
    public static Optional<SubjectId> pendingOwner(FrontierWorldState s,SubjectId actor){
        var healing=s.humanPopulation().clinical().healing().get(actor);if(healing!=null)return Optional.of(healing.actor().execution().activityOwnerId());
        return s.humanPopulation().clinical().tasks().values().stream().filter(t->t.pending()&&(t.actor().equals(actor)||t.tend().isPresent()&&t.patient().equals(actor))).map(ClinicalTask::id).findFirst();
    }
    public static boolean alive(FrontierWorldState s,SubjectId actor){var v=s.actorLocations().get(actor);return v!=null&&v.condition().status()==ActorLifeStatus.ALIVE;}
    public static int quality(FrontierWorldState s,ClinicalTask task){return s.bootstrap().ruleset().clinical().quality(s.humanPopulation().resident(task.actor()).skill(ResidentSkill.MEDICINE),task.role()==ClinicalTask.Role.PATIENT);}
    public static boolean needsCare(FrontierWorldState s,SubjectId actor){
        if(!enabled(s)||!alive(s,actor))return false;
        var wound=s.humanPopulation().clinical().wounds().get(actor);
        return wound!=null&&(wound.untreated()>0&&ActorSupplyVisits.stockPresent(s,actor,s.humanPopulation().resident(actor).settlementId(),s.bootstrap().ruleset().clinical().bandage())
                ||wound.observedHealth()<wound.maximumHealth()*s.bootstrap().ruleset().clinical().restBelowPermille()/1000);
    }
    public static boolean wantsRest(FrontierWorldState s,SubjectId actor){
        var wound=s.humanPopulation().clinical().wounds().get(actor);if(wound==null)return false;
        var task=s.humanPopulation().clinical().taskFor(actor);
        int threshold=s.humanPopulation().restPlaces().uses().containsKey(actor)||task.filter(t->t.role()==ClinicalTask.Role.PATIENT&&t.phase()==ClinicalTask.Phase.REST).isPresent()
                ?s.bootstrap().ruleset().clinical().finishRestPermille():s.bootstrap().ruleset().clinical().restBelowPermille();
        return wound.observedHealth()<wound.maximumHealth()*threshold/1000;
    }
    public static boolean pending(FrontierWorldState s,ClinicalTask task){
        var rest=s.humanPopulation().restPlaces().uses().get(task.actor());
        return task.pending()||pendingOwner(s,task.actor()).isPresent()
                ||rest!=null&&(rest.pendingBody().isPresent()||rest.phase()==RestUse.Phase.RESTING&&alive(s,task.actor()));
    }
    public static long recovery(FrontierWorldState s,ClinicalWound wound,long tick){
        // First cut has no transient physiology multipliers. In particular, the nutrition at
        // an interval's end cannot retroactively remove or grant that interval's recovery.
        var rules=s.bootstrap().ruleset().clinical();long rate=rules.naturalUnitsPerTick();
        var use=s.humanPopulation().restPlaces().uses().get(wound.actor());
        if(use==null)return wound.recoveryUnits(tick,rate,rules);
        long total=0;var projected=wound;var intervals=new ArrayList<RestUse.Interval>();
        use.completed().ifPresent(intervals::add);
        if(use.phase()==RestUse.Phase.RESTING)intervals.add(new RestUse.Interval(use.changedAt(),tick));
        for(var interval:intervals){
            long start=Math.max(projected.evaluatedAt(),interval.start()),end=Math.min(tick,interval.end());if(end<=start)continue;
            long ordinary=projected.recoveryUnits(start,rate,rules);total+=ordinary;
            projected=projected.observe(new FixedScalar(projected.observedHealth()+ordinary),start);
            long rest=projected.recoveryUnits(end,rate*use.place().profile().recoveryPermille()/1000,rules);total+=rest;
            projected=projected.observe(new FixedScalar(projected.observedHealth()+rest),end);
        }
        return total+projected.recoveryUnits(tick,rate,rules);
    }
    public static FrontierWorldState update(FrontierWorldState s,ClinicalState clinical){return s.withChanges(FrontierWorldStateUpdate.begin().humanPopulation(s.humanPopulation().withClinical(clinical)));}
    public static ClinicalWound observed(FrontierWorldState s,SubjectId actor,long tick){
        var health=s.actorLocations().get(actor).condition().health();var old=s.humanPopulation().clinical().wounds().get(actor);
        return old==null?ClinicalWound.observed(actor,health,ActorCondition.HEALTHY.health(),tick):old.observedHealth()==health.raw()?old:old.observe(health,tick);
    }
    public static boolean nearPatient(FrontierWorldState s,ClinicalTask task){
        return procedureStation(s,task,s.actorLocations().get(task.actor()).supportingSurface());
    }
    /** A procedure uses a neighbouring contact, rather than a radius that can reach through a room wall. */
    public static boolean procedureStation(FrontierWorldState s,ClinicalTask task,SurfaceAnchor from){
        var target=s.actorLocations().get(task.patient()).supportingSurface();
        if(task.actor().equals(task.patient()))return from.equals(target);
        var rest=s.humanPopulation().restPlaces().uses().get(task.patient());
        if(rest!=null&&rest.phase()==RestUse.Phase.RESTING){
            if(from.support().equals(rest.place().head())||from.support().equals(rest.place().foot()))return false;
            target=new SurfaceAnchor(rest.place().head().offset(0,-1,0));
        }
        return Math.abs((long)from.x()-target.x())+Math.abs((long)from.z()-target.z())==1&&Math.abs((long)from.y()-target.y())<=1;
    }
    private ClinicalAuthority(){}
}
