package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.clinical.*;
import java.util.*;
/** Only the short supply visit participates in warehouse access, never a patient or resting medic. */
final class ClinicalServiceAccess implements ServiceAccessCapability {
    public ServiceAccessDemand.Kind kind(){return ServiceAccessDemand.Kind.CLINICAL_SUPPLY;}
    public ServiceAccessDemand.Priority priority(){return ServiceAccessDemand.Priority.SELF_CARE;}
    public List<ServiceAccessDemand> demands(FrontierWorldState s,SubjectId point){
        if(!ReferenceContainerCustody.isReferenceContainer(s,point))return List.of();
        var boundary=ServiceAccessCoordinator.boundary(s,point);var result=new ArrayList<ServiceAccessDemand>();
        for(var task:s.humanPopulation().clinical().tasks().values()){
            if(!ClinicalAuthority.current(s,task)||!ClinicalAuthority.alive(s,task.actor())||task.supply().isEmpty())continue;
            var visit=task.supply().orElseThrow();var identity=ActorSupplyVisits.service(visit,kind()).filter(i->i.pointId().equals(point));if(identity.isEmpty())continue;
            result.add(new ServiceAccessDemand(identity.orElseThrow(),priority(),ServiceAccessCoordinator.occupies(s,boundary,task.actor())?ServiceAccessDemand.Presence.OCCUPIED:ServiceAccessDemand.Presence.APPROACH,
                    task.startedAt(),visit.pending().isPresent(),ActorSupplyVisits.available(s,visit)));
        }
        return List.copyOf(result);
    }
    public Set<SubjectId> wakePoints(FrontierWorldState before,FrontierWorldState after){
        return ServiceAccessWakeDependencies.changed(before.humanPopulation().clinical().tasks(),after.humanPopulation().clinical().tasks(),
                task->task.supply().flatMap(v->ActorSupplyVisits.service(v,kind())).stream().map(ServiceAccessDemand.Identity::pointId));
    }
}
