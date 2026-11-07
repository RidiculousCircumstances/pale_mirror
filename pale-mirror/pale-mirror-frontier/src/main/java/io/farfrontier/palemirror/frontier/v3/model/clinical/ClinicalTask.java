package io.farfrontier.palemirror.frontier.v3.model.clinical;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Patient stays and short clinician visits have separate executions and independent completion. */
public record ClinicalTask(SubjectId id,SubjectId home,SubjectId patient,ActorExecutionId execution,Role role,Phase phase,
        long revision,SurfaceAnchor station,Optional<ActorSupplyVisit> supply,Optional<ClinicalTend> tend,long startedAt){
    public enum Role { PATIENT(1), MEDIC(2); private final int tag; Role(int tag){this.tag=tag;} public int wireTag(){return tag;} public static Role fromWireTag(int tag){for(var v:values())if(v.tag==tag)return v;throw new
        IllegalArgumentException("unknown clinical role");} }
    public enum Phase { SELECT(1), SUPPLY(2), APPROACH(3), TEND(4), REST(5); private final int tag; Phase(int tag){this.tag=tag;} public int wireTag(){return tag;} public static Phase fromWireTag(int tag){for(var
        v:values())if(v.tag==tag)return v;throw new IllegalArgumentException("unknown clinical phase");} }
    public ClinicalTask {
        Objects.requireNonNull(id);Objects.requireNonNull(home);Objects.requireNonNull(patient);Objects.requireNonNull(execution);
        Objects.requireNonNull(role);Objects.requireNonNull(phase);Objects.requireNonNull(station);Objects.requireNonNull(supply);Objects.requireNonNull(tend);
        if(execution.activityKind()!=io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.CLINICAL_CARE||!execution.activityOwnerId().equals(id)||revision<1||startedAt<0||(role==Role.PATIENT)!=patient.equals(execution.actorId())
                ||supply.filter(v->!v.execution().equals(execution)||!v.economicOwner().equals(home)).isPresent()||tend.filter(v->!v.performer().execution().equals(execution)||!v.patient().execution().actorId().equals(patient)).isPresent()
                ||tend.isPresent()&&phase!=Phase.TEND||supply.isPresent()!=(phase==Phase.SUPPLY))
            throw new IllegalArgumentException("invalid clinical role/phase ownership");
    }
    public SubjectId actor(){return execution.actorId();}
    public boolean pending(){return tend.isPresent()||supply.flatMap(ActorSupplyVisit::pending).isPresent();}
    public MovementOrder order(){return supply.map(v->v.order().movementOrder()).orElseGet(()->new MovementOrder(id,actor(),
            Phase.APPROACH.wireTag(),revision,List.of(station),TraversalCapability.PEDESTRIAN,MovementOrder.ArrivalPolicy.EXACT_STATION));}
    public ClinicalTask phase(Phase next,SurfaceAnchor destination,long tick){
        if(pending())throw new IllegalArgumentException("clinical physical effect remains unsettled");
        return new ClinicalTask(id,home,patient,execution,role,next,revision+1,destination,Optional.empty(),Optional.empty(),tick);
    }
    public ClinicalTask withSupply(ActorSupplyVisit visit){return new ClinicalTask(id,home,patient,execution,role,Phase.SUPPLY,revision+1,visit.order().station(),Optional.of(visit),Optional.empty(),startedAt);}
    public ClinicalTask withSupplyPrepared(ActorSupplyVisit visit){
        if(!supply.map(ActorSupplyVisit::order).equals(Optional.of(visit.order())))throw new IllegalArgumentException("foreign supply preparation");
        return new ClinicalTask(id,home,patient,execution,role,phase,revision,station,Optional.of(visit),Optional.empty(),startedAt);
    }
    public ClinicalTask settled(Phase next,long tick){return new ClinicalTask(id,home,patient,execution,role,next,revision+1,station,Optional.empty(),Optional.empty(),tick);}
    public ClinicalTask withTend(ClinicalTend value){return new ClinicalTask(id,home,patient,execution,role,Phase.TEND,revision,station,Optional.empty(),Optional.of(value),startedAt);}
    public ClinicalTask resumed(ActorExecutionId successor){
        if(pending()||!actor().equals(successor.actorId())||!id.equals(successor.activityOwnerId()))throw new IllegalArgumentException("unsettled/foreign clinical continuation");
        return new ClinicalTask(id,home,patient,successor,role,Phase.SELECT,revision+1,station,Optional.empty(),Optional.empty(),startedAt);
    }
}
