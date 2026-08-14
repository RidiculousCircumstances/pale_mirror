package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
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
        helper.getLevel().setBlock(surface.above(90), Blocks.SPRUCE_LEAVES.defaultBlockState(), 2);
        helper.getLevel().setBlock(surface.above(91), Blocks.BEE_NEST.defaultBlockState(), 2);

        FrontierWorldgenFeature.clearNaturalVegetation(helper.getLevel(),
                new CompiledChunkSlice.VegetationColumn(surface.getX(), surface.getZ(), surface.getY()));

        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 4, 2));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 5, 2));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 6, 2));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 12, 2));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 13, 2));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 14, 2));
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 15, 2));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 92, 2));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 93, 2));
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void lateCleanupPreservesExactAuthoredTimber(GameTestHelper helper) {
        BlockPos surface = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos natural = surface.above(3);
        BlockPos authored = surface.above(5);
        helper.getLevel().setBlock(natural, Blocks.BIRCH_LOG.defaultBlockState(), 2);
        helper.getLevel().setBlock(natural.above(), Blocks.BIRCH_LEAVES.defaultBlockState(), 2);
        helper.getLevel().setBlock(authored, Blocks.SPRUCE_LOG.defaultBlockState(), 2);

        FrontierWorldgenFeature.clearLateNaturalVegetation(helper.getLevel(),
                new CompiledChunkSlice.VegetationColumn(surface.getX(), surface.getZ(), surface.getY()),
                java.util.Map.of(authored, Blocks.SPRUCE_LOG.defaultBlockState()));

        helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, 5, 5));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, 6, 5));
        helper.assertBlockPresent(Blocks.SPRUCE_LOG, new BlockPos(5, 7, 5));
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void settlementBlendNeverCreatesAHighCutFace(GameTestHelper helper) {
        var exact = new CompiledChunkSlice.TerrainColumn(0, 0, 70,
                Blocks.STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
        helper.assertValueEqual(70, FrontierWorldgenFeature.resolvedTarget(92,
                exact.targetY(), exact.blendDistance()), "hard building pads must retain their datum");
        for (int distance = 1; distance <= 28; distance++) {
            int resolved = FrontierWorldgenFeature.resolvedTarget(92, 70, distance);
            helper.assertValueEqual(Math.min(92, 70 + distance), resolved,
                    "blend must rise by at most one block per horizontal column");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 40)
    public static void plannedBridgeKeepsRailAboveWaterAndPreservesTheRiver(GameTestHelper helper) {
        BlockPos rail = helper.absolutePos(new BlockPos(4, 9, 4));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 3; y <= 6; y++) {
            helper.getLevel().setBlock(helper.absolutePos(new BlockPos(4 + x, y, 4 + z)),
                    Blocks.WATER.defaultBlockState(), 2);
        }
        var column = new CompiledChunkSlice.RailColumn(rail,
                Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH),
                Blocks.GRAVEL.defaultBlockState());
        FrontierWorldgenFeature.placeRailColumn(helper.getLevel(), column, rail.getY() - 2, rail.getY() - 6);

        BlockPos shallowRail = helper.absolutePos(new BlockPos(8, 6, 4));
        for (int x = 7; x <= 9; x++) for (int z = 3; z <= 5; z++) {
            helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 3, z)),
                    Blocks.WATER.defaultBlockState(), 2);
        }
        var shallowColumn = new CompiledChunkSlice.RailColumn(shallowRail,
                Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH),
                Blocks.GRAVEL.defaultBlockState());
        FrontierWorldgenFeature.placeRailColumn(helper.getLevel(), shallowColumn,
                shallowRail.getY() - 2, shallowRail.getY() - 3);

        helper.runAfterDelay(5, () -> {
            helper.assertBlockPresent(Blocks.RAIL, new BlockPos(4, 9, 4));
            helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(4, 8, 4));
            helper.assertBlockPresent(Blocks.WATER, new BlockPos(3, 6, 4));
            helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(8, 5, 4));
            helper.assertBlockPresent(Blocks.WATER, new BlockPos(7, 3, 4));
            helper.succeed();
        });
    }
}
