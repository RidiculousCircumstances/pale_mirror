package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.SiteEnvironmentPlan;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
        helper.getLevel().setBlock(surface.above(92), Blocks.MUSHROOM_STEM.defaultBlockState(), 2);
        helper.getLevel().setBlock(surface.above(93), Blocks.RED_MUSHROOM_BLOCK.defaultBlockState(), 2);

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
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 94, 2));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 95, 2));
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void authoredDecorationRetainsItsAbsoluteDatum(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(5, 7, 5));
        var decoration = new CompiledChunkSlice.AuthoredDecoration(position,
                Blocks.SPRUCE_LOG.defaultBlockState());
        helper.assertValueEqual(decoration.position(), position,
                "authored decoration must persist an absolute position rather than a heightmap offset");
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void authoredEnvironmentUsesCircularEcologyAndSurfaceOnlyStructureClearance(
            GameTestHelper helper) {
        var environment = new SiteEnvironmentPlan("test", new VisualPoint(1_000, 70, -2_000),
                128, 176, 16, 52, 96);

        helper.assertTrue(environment.insideHard(1_128, -2_000),
                "hard ecology reservation must include its 128-block circular edge");
        helper.assertTrue(!environment.insideHard(1_128, -1_872),
                "hard ecology reservation must be circular rather than a square");
        helper.assertTrue(environment.insideTransition(1_160, -2_000),
                "transition woodland must extend beyond the hard ecology reservation");
        helper.assertTrue(environment.insideStructureClearance(1_144, -2_000),
                "surface structures must respect the additional 16-block clearance");

        helper.assertTrue(WorldgenExclusionIndex.intersectsSurfaceStructure(environment,
                        new BoundingBox(1_138, 60, -2_004, 1_152, 84, -1_996)),
                "a surface structure crossing the clearance circle must be rejected as a whole");
        helper.assertTrue(!WorldgenExclusionIndex.intersectsSurfaceStructure(environment,
                        new BoundingBox(1_138, 10, -2_004, 1_152, 40, -1_996)),
                "a deep structure below authored foundations must remain available");
        helper.assertTrue(!WorldgenExclusionIndex.intersectsSurfaceStructure(environment,
                        new BoundingBox(1_150, 60, -2_004, 1_164, 84, -1_996)),
                "a surface structure wholly outside the clearance circle must remain available");
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
                Blocks.GRAVEL.defaultBlockState(), true);
        FrontierWorldgenFeature.placeRailColumn(helper.getLevel(), column, rail.getY() - 2, rail.getY() - 6);

        BlockPos shallowRail = helper.absolutePos(new BlockPos(8, 6, 4));
        for (int x = 7; x <= 9; x++) for (int z = 3; z <= 5; z++) {
            helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 3, z)),
                    Blocks.WATER.defaultBlockState(), 2);
        }
        var shallowColumn = new CompiledChunkSlice.RailColumn(shallowRail,
                Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH),
                Blocks.GRAVEL.defaultBlockState(), true);
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

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void railwayRemainsTheFinalWriterInsideItsSlice(GameTestHelper helper) {
        int x = helper.absolutePos(new BlockPos(7, 2, 7)).getX();
        int z = helper.absolutePos(new BlockPos(7, 2, 7)).getZ();
        int y = helper.getLevel().getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z);
        BlockPos rail = new BlockPos(x, y, z);
        var column = new CompiledChunkSlice.RailColumn(rail,
                Blocks.POWERED_RAIL.defaultBlockState().setValue(
                        net.minecraft.world.level.block.PoweredRailBlock.SHAPE, RailShape.NORTH_SOUTH),
                Blocks.REDSTONE_BLOCK.defaultBlockState(), true);
        helper.getLevel().setBlock(rail, Blocks.BRICKS.defaultBlockState(), 2);
        FrontierWorldgenFeature.placeRailColumn(helper.getLevel(), column, rail.getY() - 1, rail.getY() - 1);

        helper.assertValueEqual(helper.getLevel().getBlockState(rail).getBlock(), Blocks.POWERED_RAIL,
                "late module decoration must not replace the immutable railway graph");
        helper.assertValueEqual(helper.getLevel().getBlockState(rail.below()).getBlock(), Blocks.REDSTONE_BLOCK,
                "powered rail must retain a stable powered support after finalization");
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 120)
    public static void alternateMineLeavesFutureIndustrialPadsNatural(GameTestHelper helper) {
        var seed = new FrontierRegionPlanner().plan(8_741L, 0,
                new VisualPoint(4_000, 72, -4_000), FrontierClimate.TEMPERATE);
        var alternate = seed.alternateMineSite();
        var future = alternate.foundations().stream().filter(value -> value.id().equals("power"))
                .findFirst().orElseThrow();
        int x = (future.footprint().min().x() + future.footprint().max().x()) / 2;
        int z = (future.footprint().min().z() + future.footprint().max().z()) / 2;
        var catalog = new FrontierGenesisCompiler().compile(java.util.List.of(seed));
        var slice = catalog.chunk(net.minecraft.world.level.ChunkPos.asLong(x >> 4, z >> 4));

        helper.assertTrue(slice == null || slice.terrain().stream()
                        .noneMatch(value -> value.x() == x && value.z() == z),
                "an unbuilt Red Valley power project must not pre-grade a floating industrial pad");
        helper.assertTrue(slice == null || slice.blocks().entrySet().stream()
                        .filter(value -> Math.abs(value.getKey().getX() - x) <= 2
                                && Math.abs(value.getKey().getZ() - z) <= 2)
                        .noneMatch(value -> net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                .getKey(value.getValue().getBlock()).getNamespace().equals("create")),
                "an unbuilt Red Valley power project must not materialize Create machinery");
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 20)
    public static void railwayPierCadenceDependsOnRouteProgressNotWorldCoordinates(GameTestHelper helper) {
        java.util.List<VisualPoint> path = java.util.stream.IntStream.range(0, 9)
                .mapToObj(index -> new VisualPoint(101 + index, 70, 202 - index)).toList();
        java.util.List<Boolean> piers = new java.util.ArrayList<>();
        FrontierRailGenesisCompiler.compile(path, new FrontierRailGenesisCompiler.Sink() {
            @Override public void rail(BlockPos rail, net.minecraft.world.level.block.state.BlockState state,
                                       net.minecraft.world.level.block.state.BlockState support,
                                       boolean supportPier) {
                piers.add(supportPier);
            }
            @Override public void block(BlockPos position,
                                        net.minecraft.world.level.block.state.BlockState state) { }
        });

        helper.assertValueEqual(piers, java.util.List.of(true, false, false, false, true,
                        false, false, false, true),
                "rail supports must follow deterministic four-block route cadence");
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 120)
    public static void railwayCompilationOwnsAContinuousVegetationSafetyEnvelope(GameTestHelper helper) {
        var seed = new FrontierRegionPlanner().plan(61_337L, 0,
                new VisualPoint(8_000, 72, -4_000), FrontierClimate.TEMPERATE);
        var catalog = new FrontierGenesisCompiler().compile(java.util.List.of(seed));
        VisualPoint rail = seed.baselineRailNodes().get(seed.baselineRailNodes().size() / 2);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (dx * dx + dz * dz > 10) continue;
            int x = rail.x() + dx;
            int z = rail.z() + dz;
            var slice = catalog.chunk(net.minecraft.world.level.ChunkPos.asLong(x >> 4, z >> 4));
            helper.assertTrue(slice != null && slice.vegetation().stream()
                            .anyMatch(value -> value.x() == x && value.z() == z),
                    "rail vegetation envelope has a hole at " + x + "," + z);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 120)
    public static void curatedMineModulesSealRoofsAndUseFullStructuralPosts(GameTestHelper helper) {
        var seed = new FrontierRegionPlanner().plan(91_003L, 0,
                new VisualPoint(8_000, 76, 8_000), FrontierClimate.TEMPERATE);
        var blocks = new java.util.LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        seed.primaryMineSite().surfaceBuildings().stream().flatMap(value -> value.modules().stream())
                .map(AuthoredModuleCompiler::compile).flatMap(value -> value.blocks().stream())
                .forEach(value -> blocks.put(new BlockPos(value.position().x(), value.position().y(),
                        value.position().z()), value.state()));
        blocks.forEach((position, state) -> {
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)
                    && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)
                    == SlabType.BOTTOM) {
                for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    var upperNeighbour = blocks.get(position.above().relative(direction));
                    helper.assertTrue(upperNeighbour == null || !upperNeighbour.hasProperty(
                                    net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE),
                            "stepped mine roof retained a half-block daylight seam at " + position);
                }
            }
            if (state.is(net.minecraft.tags.BlockTags.LOGS)) {
                var below = blocks.get(position.below());
                helper.assertTrue(below == null
                                || !(below.getBlock() instanceof net.minecraft.world.level.block.FenceBlock),
                        "full mine roof post is balanced on a fence at " + position);
            }
            if (state.is(Blocks.CHAIN)
                    || state.getBlock() instanceof net.minecraft.world.level.block.LanternBlock) {
                var support = blocks.get(position.above());
                helper.assertTrue(support == null || !support.hasProperty(
                                net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)
                                || support.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE)
                                != SlabType.TOP,
                        "hanging mine fixture is attached to the air half of a top slab at " + position);
            }
        });
        helper.succeed();
    }
}
