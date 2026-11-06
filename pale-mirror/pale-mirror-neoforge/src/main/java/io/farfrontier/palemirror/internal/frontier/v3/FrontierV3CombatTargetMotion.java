package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.combat.CombatMotion;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import net.minecraft.world.entity.*;
import java.util.*;
/** Bounded ephemeral observation, not target authority. Real player displacement matters, not only knockback velocity. */
final class FrontierV3CombatTargetMotion {
    record ObservedMotion(double x,double z,double separating,double lateral){}
    private record Sighting(ActorActuationId observer,UUID target,int runtimeId,long at,net.minecraft.world.phys.Vec3 position,ObservedMotion motion){}
    private static final Map<Mob,Sighting> SIGHTINGS=new WeakHashMap<>();
    static ObservedMotion observe(Mob fighter,LivingEntity target,ActorActuationId observer,boolean visible,int interval){
        if(!visible){SIGHTINGS.remove(fighter);return new ObservedMotion(0,0,0,0);}
        long now=fighter.level().getGameTime();
        var prior=SIGHTINGS.get(fighter);var motion=new ObservedMotion(0,0,0,0);
        boolean same=prior!=null && prior.observer().equals(observer) && prior.target().equals(target.getUUID()) && prior.runtimeId()==target.getId();
        if(same && now==prior.at())return prior.motion();
        if(same && now>prior.at() && now-prior.at()<=2L*interval){
            var velocity=CombatMotion.between(prior.position().x,prior.position().z,target.getX(),target.getZ(),now-prior.at());
            var radial=target.position().subtract(fighter.position()).multiply(1,0,1).normalize();
            motion=new ObservedMotion(velocity.x(),velocity.z(),velocity.separating(radial.x,radial.z),velocity.lateral(radial.x,radial.z));
        }
        SIGHTINGS.put(fighter,new Sighting(observer,target.getUUID(),target.getId(),now,target.position(),motion));return motion;
    }
    private FrontierV3CombatTargetMotion(){}
}
