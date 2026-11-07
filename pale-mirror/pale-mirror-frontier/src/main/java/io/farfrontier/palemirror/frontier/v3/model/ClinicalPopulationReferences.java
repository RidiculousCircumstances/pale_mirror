package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.clinical.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.rest.*;
import java.util.*;
/** Hydration rejects foreign people before their care or recovery can enter dispatch. */
public final class ClinicalPopulationReferences {
    private static final ImmutableInvariantCheck<Void> WOUNDS=new ImmutableInvariantCheck<>(),TASKS=new ImmutableInvariantCheck<>(),HEALING=new ImmutableInvariantCheck<>(),SUPPLIED=new ImmutableInvariantCheck<>(),REST=new ImmutableInvariantCheck<>();
    public static void validate(Map<SubjectId,ResidentProfile> people,ClinicalState clinical,RestPlaceState rest){
        WOUNDS.validate(()->{if(!people.keySet().containsAll(clinical.wounds().keySet()))throw new IllegalArgumentException("foreign wound actor");},people,clinical.wounds());
        HEALING.validate(()->{if(!people.keySet().containsAll(clinical.healing().keySet()))throw new IllegalArgumentException("foreign healing actor");},people,clinical.healing());
        SUPPLIED.validate(()->{if(!people.keySet().containsAll(clinical.supplied().keySet()))throw new IllegalArgumentException("foreign completed personal supply");},people,clinical.supplied());
        TASKS.validate(()->{for(var task:clinical.tasks().values()){
            var actor=people.get(task.actor());var patient=people.get(task.patient());
            if(actor==null||patient==null||!actor.settlementId().equals(task.home())||!patient.settlementId().equals(task.home()))throw new IllegalArgumentException("foreign clinical patient/performer/home");
        }},people,clinical.tasks());
        REST.validate(()->{for(var use:rest.uses().values()){
            var actor=people.get(use.execution().actorId());if(actor==null||!actor.settlementId().equals(use.place().home()))throw new IllegalArgumentException("foreign residential rest reservation");
        }},people,rest.uses());
    }
    private ClinicalPopulationReferences(){}
}
