package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import net.minecraft.world.entity.*;
import java.util.*;
/** Bounded disposable native observation. The canonical stance retains only dated witnessed facts. */
final class FrontierV3CombatObservation {
    private record Sample(ActorActuationId observer,UUID target,int runtimeId,long at,boolean swing,boolean harmed,long began,long missed){}
    private static final Map<Mob,Sample> SAMPLES=new WeakHashMap<>();
    static CombatThreatObservation observe(Mob body,LivingEntity target,ActorActuationId actor,boolean visible,boolean swing,long tick,int opponents){
        if(!visible){SAMPLES.remove(body);return CombatThreatObservation.none();}
        var prior=SAMPLES.get(body);
        if(prior==null || !prior.observer().equals(actor) || !prior.target().equals(target.getUUID()) || prior.runtimeId()!=target.getId() || tick<prior.at() || tick-prior.at()>12)
            prior=new Sample(actor,target.getUUID(),target.getId(),tick,false,false,-1,-1);
        boolean harmed=swing?(body.hurtTime>0 || prior.swing() && prior.harmed()):false;
        long began=swing && !prior.swing()?tick:prior.began();long missed=!swing && prior.swing() && !prior.harmed()?tick:prior.missed();
        SAMPLES.put(body,new Sample(actor,target.getUUID(),target.getId(),tick,swing,harmed,began,missed));
        return new CombatThreatObservation(swing?began:-1,missed,opponents);
    }
    private FrontierV3CombatObservation(){}
}
