package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import io.farfrontier.palemirror.frontier.v3.model.combat.WeaponProfile;
import net.minecraft.world.entity.*;
/** Native hitboxes account for different dimensions, height and weapon interaction range. */
final class FrontierV3MeleeReach {
    static boolean contains(LivingEntity attacker,LivingEntity target,WeaponProfile profile){
        return target.getBoundingBox().distanceToSqr(attacker.getEyePosition())<=profile.reach()*profile.reach();
    }
    private FrontierV3MeleeReach(){}
}
