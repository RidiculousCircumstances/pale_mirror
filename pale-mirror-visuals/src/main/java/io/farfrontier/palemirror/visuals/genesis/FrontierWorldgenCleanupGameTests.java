package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PaleMirrorVisualsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierWorldgenCleanupGameTests {
    private FrontierWorldgenCleanupGameTests() { }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void gradingCleanupRemovesNaturalPlantsButPreservesConstruction(GameTestHelper helper) {
        BlockPos surface = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(surface.above(2), Blocks.DANDELION.defaultBlockState(), 2);
        helper.getLevel().setBlock(surface.above(3), Blocks.OAK_LOG.defaultBlockState(), 2);
        helper.getLevel().setBlock(surface.above(4), Blocks.OAK_LEAVES.defaultBlockState(), 2);
        helper.getLevel().setBlock(surface.above(10), Blocks.OAK_LEAVES.defaultBlockState(), 2);
        helper.getLevel().setBlock(surface.above(11), Blocks.BEE_NEST.defaultBlockState(), 2);
        helper.getLevel().setBlock(surface.above(12), Blocks.VINE.defaultBlockState(), 2);
        helper.getLevel().setBlock(surface.above(13), Blocks.STONE.defaultBlockState(), 2);

        FrontierWorldgenFeature.clearNaturalVegetation(helper.getLevel(),
                new CompiledChunkSlice.VegetationColumn(surface.getX(), surface.getZ(), surface.getY()));

        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 4, 2));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 5, 2));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 6, 2));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 12, 2));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 13, 2));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 14, 2));
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 15, 2));
        helper.succeed();
    }
}
