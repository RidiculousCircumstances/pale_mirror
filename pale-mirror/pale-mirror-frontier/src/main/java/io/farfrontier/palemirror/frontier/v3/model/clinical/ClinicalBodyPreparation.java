package io.farfrontier.palemirror.frontier.v3.model.clinical;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
/** The same analytical recovery reducer settles COLD time before the body owner captures initial native HP. */
public final class ClinicalBodyPreparation implements ActorBodyPreparation.Port {
    public ActorBodyPreparation.Key key(){return ActorBodyPreparation.Key.CLINICAL_RECOVERY;}
    public Set<FrontierWorldStateUpdate.Component> ownedComponents(){return Set.of(FrontierWorldStateUpdate.Component.HUMAN_POPULATION);}
    public ActorBodyPreparation.Acknowledgement prepare(FrontierWorldState s,SubjectId actor,long tick){
        var basis=s;
        if(!ClinicalAuthority.enabled(s)||!s.humanPopulation().residents().containsKey(actor)||!ClinicalAuthority.alive(s,actor)
                ||ActorInventoryInteractionFences.pending(s,actor))return ActorBodyPreparation.Acknowledgement.unchanged(basis,actor,tick);
        var wound=s.humanPopulation().clinical().wounds().get(actor);
        if(wound==null||tick<wound.evaluatedAt())return ActorBodyPreparation.Acknowledgement.unchanged(basis,actor,tick);
        if(wound.observedHealth()!=s.actorLocations().get(actor).condition().health().raw()){
            s=ClinicalTransitions.apply(s,actor,new ClinicalEvent.Vitality(actor,Optional.of(wound),tick),tick);
            wound=s.humanPopulation().clinical().wounds().get(actor);if(wound==null)return ActorBodyPreparation.Acknowledgement.unchanged(basis,actor,tick);
        }
        var after=ClinicalTransitions.apply(s,actor,new ClinicalEvent.Recovered(actor,Optional.empty(),tick,ClinicalAuthority.recovery(s,wound,tick),true),tick);
        return new ActorBodyPreparation.Acknowledgement(basis,actor,tick,basis.actorLocations().get(actor).condition(),after.actorLocations().get(actor).condition(),
                FrontierWorldStateUpdate.begin().humanPopulation(after.humanPopulation()));
    }
}
