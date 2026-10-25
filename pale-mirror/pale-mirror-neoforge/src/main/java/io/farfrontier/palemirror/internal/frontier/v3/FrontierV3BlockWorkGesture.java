package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Cosmetic station work. The caller proves its current labour/actuator; this grants no progress or movement. */
final class FrontierV3BlockWorkGesture {
    private FrontierV3BlockWorkGesture() { }
    static void present(Mob worker, BlockPosition target) {
        var position = new BlockPos(target.x(), target.y(), target.z());
        if (!FrontierV3LoadedWorld.ready((net.minecraft.server.level.ServerLevel) worker.level(), position)) return;
        worker.getLookControl().setLookAt(Vec3.atCenterOf(position));
        if (!worker.swinging) {
            var sound = worker.level().getBlockState(position).getSoundType(worker.level(), position, worker);
            worker.playSound(sound.getHitSound(), sound.getVolume() * 0.5F, sound.getPitch());
        }
        worker.swing(InteractionHand.MAIN_HAND);
    }
}
