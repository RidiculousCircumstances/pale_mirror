package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native collision proof for retained managed-body physics after an ordinary support removal. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3AmbientPhysicsGameTests {
    private FrontierV3AmbientPhysicsGameTests() { }

    @GameTest(batch = "pm-frontier-v3-ambient-physics", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void retainedResidentAndBioformFallAfterSupportRemovalWithoutCoordinateReset(GameTestHelper helper) {
        // Raise the temporary supports above the bastion template's own floor.  The lower owned
        // floor leaves an unambiguous three-block ordinary fall rather than a one-block fixture
        // settle against template scenery.
        BlockPos residentSupport = helper.absolutePos(new BlockPos(2, 4, 2));
        BlockPos bioformSupport = helper.absolutePos(new BlockPos(5, 4, 2));
        // A lower ordinary floor makes landing/collision observable after the player-equivalent
        // support removal; neither body is moved by the test after its initial materialization.
        helper.getLevel().setBlock(residentSupport.below(3), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(bioformSupport.below(3), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(residentSupport, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(bioformSupport, Blocks.STONE.defaultBlockState(), 3);
        Villager resident = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(2.5D, 5.0D, 2.5D));
        Zombie bioform = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(5.5D, 5.0D, 2.5D));
        resident.setNoGravity(true); bioform.setNoGravity(true);
        FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(resident);
        FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(bioform);
        double residentInitialY = resident.getY(), bioformInitialY = bioform.getY();

        helper.runAtTickTime(2, () -> {
            helper.getLevel().setBlock(residentSupport, Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(bioformSupport, Blocks.AIR.defaultBlockState(), 3);
        });
        // Mirror the normal HOT executor cadence.  The bridge owns only NoAI's missing vertical
        // travel half; neither test body receives an X/Z target or a coordinate reset.
        for (int tick = 3; tick < 30; tick++) {
            helper.runAtTickTime(tick, () -> {
                FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(resident);
                FrontierV3ControlledMobMotion.restoreOrdinaryPhysics(bioform);
            });
        }
        helper.runAtTickTime(30, () -> {
            helper.assertFalse(resident.isNoGravity() || bioform.isNoGravity(),
                    "a retained managed body must clear legacy no-gravity before ordinary physics");
            helper.assertTrue(resident.getY() < residentInitialY - 2.0D && bioform.getY() < bioformInitialY - 2.0D,
                    "resident and bioform must fall after their support is removed rather than hover or snap back: resident="
                            + resident.position() + " bioform=" + bioform.position());
            helper.assertTrue(Math.abs(resident.getY() - (residentSupport.getY() - 2.0D)) < 1.0E-6D
                            && Math.abs(bioform.getY() - (bioformSupport.getY() - 2.0D)) < 1.0E-6D,
                    "ordinary collision must retain both managed bodies exactly on the declared lower support: resident="
                            + resident.position() + " bioform=" + bioform.position());
            helper.succeed();
        });
    }
}
