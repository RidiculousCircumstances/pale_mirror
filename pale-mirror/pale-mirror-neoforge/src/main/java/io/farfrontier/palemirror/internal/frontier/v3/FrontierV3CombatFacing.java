package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Native combat presentation for externally controlled bodies. Does not acquire movement or damage authority. */
final class FrontierV3CombatFacing {
    private FrontierV3CombatFacing() { }
    static void face(Mob body, Vec3 target) {
        // NoAI actors do not tick vanilla LookControl. Queueing setLookAt alone
        // leaves the native head/body facing the preceding movement direction.
        body.lookAt(EntityAnchorArgument.Anchor.EYES, target);
        body.yBodyRot = body.getYRot();
    }
}
