package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Physical regression for the common body capture, including its vanilla save hook. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3BodyObservationGameTests {
    private FrontierV3BodyObservationGameTests() { }

    @GameTest(batch = "pm-frontier-v3-scene-harvest-support", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 20)
    public static void farmerReleaseCapturesRealCollisionSupportButNotAnAirborneBody(GameTestHelper helper) {
        BlockPos support = helper.absolutePos(new BlockPos(2, 0, 2));
        helper.getLevel().setBlock(support, Blocks.FARMLAND.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(support.above(2), Blocks.AIR.defaultBlockState(), 3);
        Villager worker = helper.spawnWithNoFreeWill(EntityType.VILLAGER,
                new Vec3(2.5D, 0.9375D, 2.5D));
        helper.runAtTickTime(2, () -> {
            helper.assertValueEqual(FrontierV3SupportedBodyCapture.observe(helper.getLevel(), worker),
                    java.util.Optional.of(new BodyPosition(support.getX(), support.getY() + 1, support.getZ())),
                    "the release body follows the real farmland collision top, not the transient onGround flag");
            BodyPosition expected = new BodyPosition(support.getX(), support.getY() + 1, support.getZ());
            helper.assertValueEqual(FrontierV3AmbientActorExecutor.observedBody(worker), expected,
                    "ambient WORK release must agree with harvest on fractional farmland feet");
            helper.assertValueEqual(FrontierV3SurfaceObservation.observedBody(worker), expected,
                    "navigation observations share the same body conversion");
            helper.assertTrue(FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, expected.supportingSurface()),
                    "supported farmland feet satisfy the common semantic arrival contract");
            Villager neighbour = helper.spawnWithNoFreeWill(EntityType.VILLAGER,
                    new Vec3(2.5D, 0.9375D, 2.5D));
            helper.assertTrue(!FrontierV3SemanticMovement.targetIsNavigable(helper.getLevel(), worker, expected.supportingSurface()),
                    "another living body still blocks prospective admission to the station");
            helper.assertTrue(FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, expected.supportingSurface()),
                    "a neighbour cannot revoke the current worker's observed support or prepared effect station");
            neighbour.discard();
            helper.getLevel().setBlock(support.above(), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(!FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, expected.supportingSurface()),
                    "real block intrusion still invalidates actual station clearance");
            helper.getLevel().setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertValueEqual(FrontierV3SupportedBodyCapture.observeDeparting(helper.getLevel(), worker),
                    java.util.Optional.of(expected), "departure uses the same supported body");
            worker.getPersistentData().putString(FrontierV3ActorCarrierComposition.ACTOR_KEY, "resident:body-observation");
            var saved = worker.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            helper.assertValueEqual(FrontierV3BodyObservationSave.read(saved, worker.getX(), worker.getY(), worker.getZ()),
                    java.util.Optional.of(expected), "vanilla save carries the exact support-normalized observation");
            helper.getLevel().setBlock(support, Blocks.STONE_SLAB.defaultBlockState(), 3);
            worker.setPos(worker.getX(), support.getY() + 0.5D, worker.getZ());
            helper.assertValueEqual(FrontierV3AmbientActorExecutor.observedBody(worker), expected,
                    "partial slab support also preserves nominal feet height");
            worker.setPos(worker.getX(), worker.getY() + 1.0D, worker.getZ());
            helper.assertTrue(FrontierV3SupportedBodyCapture.observe(helper.getLevel(), worker).isEmpty(),
                    "an airborne body must not be labelled as supported by stale getOnPos data");
            helper.assertTrue(FrontierV3BodyObservation.capture(worker).support().isEmpty(),
                    "the common observation explicitly classifies an unsupported pose");
            helper.assertTrue(!FrontierV3SemanticMovement.arrived(helper.getLevel(), worker, expected.supportingSurface()),
                    "stale Minecraft support cannot confirm an airborne semantic arrival");
            worker.discard(); helper.succeed();
        });
    }
}
