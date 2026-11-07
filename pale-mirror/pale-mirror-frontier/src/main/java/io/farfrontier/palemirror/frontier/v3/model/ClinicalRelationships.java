package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.clinical.*;
import java.util.*;
import static io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships.*;
/** Exact live care/rest identities. Unclaimed supply choices and settled native preimages are retained expectations, not live resource claims. */
public final class ClinicalRelationships {
    static void collect(FrontierWorldState s,List<Edge> target){
        for(var task:s.humanPopulation().clinical().tasks().values()){
            var owner=new SubjectEndpoint(EntityKind.CLINICAL_TASK,task.id());
            target.add(declaredEdge(Kind.CLINICAL_HOME,owner,owner,new SubjectEndpoint(EntityKind.SETTLEMENT,task.home()),Lifecycle.ACTIVE,"clinical"));
            target.add(declaredEdge(Kind.CLINICAL_PERFORMER,owner,owner,new SubjectEndpoint(EntityKind.RESIDENT,task.actor()),Lifecycle.ACTIVE,"clinical"));
            target.add(declaredEdge(Kind.CLINICAL_PATIENT,owner,owner,new SubjectEndpoint(EntityKind.RESIDENT,task.patient()),Lifecycle.ACTIVE,"clinical"));
            if(s.humanPopulation().restPlaces().uses().containsKey(task.actor()))target.add(declaredEdge(Kind.CLINICAL_REST,owner,owner,new SubjectEndpoint(EntityKind.REST_USE,task.actor()),Lifecycle.ACTIVE,"clinical"));
        }
        for(var use:s.humanPopulation().restPlaces().uses().values()){
            var owner=new SubjectEndpoint(EntityKind.REST_USE,use.execution().actorId());
            target.add(declaredEdge(Kind.REST_ACTOR,owner,owner,new SubjectEndpoint(EntityKind.RESIDENT,use.execution().actorId()),Lifecycle.ACTIVE,"rest"));
            target.add(declaredEdge(Kind.REST_BUILDING,owner,owner,new SubjectEndpoint(EntityKind.STRUCTURE,use.place().id().buildingId()),Lifecycle.ACTIVE,use.place().id().localId()));
        }
    }
    static void validate(FrontierWorldState s){
        ClinicalPopulationReferences.validate(s.humanPopulation().residents(),s.humanPopulation().clinical(),s.humanPopulation().restPlaces());
        for(var task:s.humanPopulation().clinical().tasks().values()){
            var authority=s.actorExecutions().actors().get(task.actor());
            if(ClinicalAuthority.alive(s,task.actor())&&(authority==null||!authority.current().equals(Optional.of(task.execution()))&&!authority.continuations().contains(task.execution())))
                throw new IllegalArgumentException("clinical task has no exact current or retained execution");
            task.supply().flatMap(ActorSupplyVisit::pending).ifPresent(step->ActorBodyAuthority.requireObservation(s,step.observation().actuation().body()));
            task.tend().ifPresent(tend->{ActorBodyAuthority.requireObservation(s,tend.performer().body());ActorBodyAuthority.requireObservation(s,tend.patient().body());});
        }
        for(var healing:s.humanPopulation().clinical().healing().values())ActorBodyAuthority.requireObservation(s,healing.actor().body());
        for(var use:s.humanPopulation().restPlaces().uses().values()){
            ActorExecutionComposition.CAPABILITIES.require(use.execution().activityKind()).validateRestReference(s,use.execution(),use);
            var authority=s.actorExecutions().actors().get(use.execution().actorId());
            if(ClinicalAuthority.alive(s,use.execution().actorId())&&(authority==null||!authority.current().equals(Optional.of(use.execution()))&&!authority.continuations().contains(use.execution())))throw new
                IllegalArgumentException("rest reservation has no exact retained execution");
            use.pendingBody().ifPresent(body->ActorBodyAuthority.requireObservation(s,body.body()));
        }
    }
    private ClinicalRelationships(){}
}
