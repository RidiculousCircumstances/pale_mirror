package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.WeaponProfile;
import net.minecraft.world.entity.*;
/** Native hitboxes account for different dimensions, height and weapon interaction range. */
final class FrontierV3MeleeReach {
    static boolean contains(LivingEntity attacker,LivingEntity target,WeaponProfile profile){
        return target.getBoundingBox().distanceToSqr(attacker.getEyePosition())<=profile.reach()*profile.reach();
    }
    /** Bodies interposed on the actual contact segment matter, including friendly guards in a choke. */
    static boolean clearContact(LivingEntity attacker,LivingEntity target){
        var start=attacker.getEyePosition();var end=target.getBoundingBox().getCenter();
        var area=new net.minecraft.world.phys.AABB(start,end);
        return attacker.level().getEntitiesOfClass(LivingEntity.class,area,
                body->body!=attacker && body!=target && body.isAlive() && !body.isSpectator())
                .stream().noneMatch(body->body.getBoundingBox().clip(start,end).isPresent());
    }
    private FrontierV3MeleeReach(){}
}
