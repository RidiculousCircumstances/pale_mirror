package io.farfrontier.palemirror.frontier.v3.model.clinical;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;

/** Exact finite dressing declaration. A self procedure names one body, not two leases for the same actor. */
public record ClinicalTend(ActorActuationId performer,ActorActuationId patient,PhysicalStackBinding bandage,
        SubjectId lot,ClinicalWound wound,int quality,long coverage,long attempt,long startedAt,int duration){
    public ClinicalTend {
        Objects.requireNonNull(performer);Objects.requireNonNull(patient);Objects.requireNonNull(bandage);Objects.requireNonNull(lot);Objects.requireNonNull(wound);
        if(performer.execution().activityKind()!=io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.CLINICAL_CARE
                ||performer.execution().actorId().equals(patient.execution().actorId())&&!performer.equals(patient)||!(bandage.address() instanceof PhysicalStackAddress.ActorStack a)||!a.actorId().equals(performer.execution().actorId())
                ||bandage.lotQuantities().getOrDefault(lot,0)<1||!bandage.claimQuantities().isEmpty()||!wound.actor().equals(patient.execution().actorId())
                ||quality<1||quality>1000||coverage<1||attempt<1||startedAt<0||duration<1)
            throw new IllegalArgumentException("invalid exact clinical procedure");
    }
    public long harmRevision(){return wound.harmRevision();}
    public boolean self(){return performer.equals(patient);}
}
