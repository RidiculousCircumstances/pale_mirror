package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exhaustive structural gate; manual screenshot review only needs the nine representative layouts. */
@GameTestHolder(PaleMirrorVisualsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterializationMatrixGameTests {
    private MaterializationMatrixGameTests() { }

    @GameTest(templateNamespace = "pale_mirror_visuals", template = "gametest_empty", timeoutTicks = 600)
    public static void all216PublicRealmVariantsHaveSupportedMicrogeometry(GameTestHelper helper) {
        VisualPoint anchor = new VisualPoint(12_000, 76, -9_000);
        int combinations = 0;
        for (FrontierClimate climate : FrontierClimate.values()) {
            for (int relief : new int[]{2, 7, 14}) {
                for (int variant = 0; variant < SettlementLayoutPlanner.VARIANT_COUNT; variant++) {
                    int combination = combinations;
                    int layoutVariant = variant;
                    var terrain = new TerrainCandidate(anchor, relief, 0, 0, relief);
                    var seed = new FrontierRegionPlanner().plan(47_113L + combination, combination,
                            terrain, climate,
                            (requirement, candidates) -> new MountainMineAnchor(candidates.getFirst(), 0),
                            FrontierRegionPlanner::gradedManhattanRail,
                            (source, settlementAnchor, selectedClimate, direction, selectedTerrain) ->
                                    new SettlementLayoutPlanner().planKnownVariant(source, settlementAnchor,
                                            selectedClimate, direction, selectedTerrain,
                                            SettlementTerrainSnapshot.flat(settlementAnchor), layoutVariant));
                    var catalog = new FrontierGenesisCompiler().compile(java.util.List.of(seed));
                    var decorations = catalog.chunks().values().stream()
                            .flatMap(slice -> slice.decorations().stream()).toList();
                    Map<SurfaceSlot, BlockState> states = new HashMap<>();
                    java.util.Set<SurfaceSlot> occupied = new java.util.HashSet<>();
                    seed.settlementSite().surfacePlan().columns().forEach(value -> occupied.add(
                            new SurfaceSlot(value.x(), value.z(), value.groundY() - 1)));
                    catalog.chunks().values().stream().flatMap(slice -> slice.terrain().stream())
                            .forEach(value -> occupied.add(new SurfaceSlot(value.x(), value.z(), value.targetY())));
                    catalog.chunks().values().stream().flatMap(slice -> slice.blocks().keySet().stream())
                            .forEach(value -> occupied.add(new SurfaceSlot(value.getX(), value.getZ(), value.getY())));
                    decorations.forEach(value -> {
                        SurfaceSlot slot = new SurfaceSlot(value.position().getX(), value.position().getZ(),
                                value.position().getY());
                        helper.assertTrue(states.put(slot, value.state()) == null,
                                "duplicate surface slot in combination " + combination + ": " + slot);
                        occupied.add(slot);
                    });
                    helper.assertTrue(decorations.stream()
                                    .anyMatch(value -> value.state().getBlock() instanceof SlabBlock),
                            "public realm has no slab articulation in combination " + combination);
                    assertFlatAccessUsesLevelPaving(helper, seed, catalog, combination);
                    assertSupportedFixtures(helper, states, occupied, combination);
                    combinations++;
                }
            }
        }
        helper.assertValueEqual(combinations, 216,
                "matrix must cover 3 climates x 3 archetypes x 24 layout variants");
        helper.succeed();
    }

    private static void assertFlatAccessUsesLevelPaving(
            GameTestHelper helper, io.farfrontier.palemirror.api.AuthoredRegionSeed seed,
            CompiledGenesisCatalog catalog, int combination) {
        Map<Long, BlockState> terrain = new HashMap<>();
        catalog.chunks().values().stream().flatMap(slice -> slice.terrain().stream()).forEach(column ->
                terrain.put(net.minecraft.world.level.ChunkPos.asLong(column.x(), column.z()), column.surface()));
        seed.settlementSite().circulation().stream()
                .filter(feature -> feature.kind() == io.farfrontier.palemirror.api.LinearFeatureKind.STAIRS)
                .filter(feature -> feature.nodes().stream().map(io.farfrontier.palemirror.api.VisualPoint::y)
                        .distinct().count() == 1)
                .forEach(feature -> feature.nodes().forEach(point -> {
                    BlockState state = terrain.get(net.minecraft.world.level.ChunkPos.asLong(point.x(), point.z()));
                    helper.assertTrue(state == null || !(state.getBlock() instanceof StairBlock),
                            "flat access became a stair trench in combination " + combination + " at " + point);
                }));
    }

    private static void assertSupportedFixtures(GameTestHelper helper, Map<SurfaceSlot, BlockState> states,
                                                java.util.Set<SurfaceSlot> occupied, int combination) {
        states.forEach((slot, state) -> {
            if (state.getBlock() instanceof LanternBlock) {
                boolean hanging = state.getValue(LanternBlock.HANGING);
                helper.assertTrue(occupied.contains(slot.above(hanging ? 1 : -1)),
                        "unsupported lantern in combination " + combination + ": " + slot);
            }
            if ((state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock)
                    && slot.offsetY() > 0) {
                boolean connected = occupied.contains(slot.above(-1))
                        || occupied.contains(slot.east()) || occupied.contains(slot.west())
                        || occupied.contains(slot.north()) || occupied.contains(slot.south());
                helper.assertTrue(connected,
                        "floating fence/wall in combination " + combination + ": " + slot);
            }
        });
    }

    private record SurfaceSlot(int x, int z, int offsetY) {
        SurfaceSlot above(int amount) { return new SurfaceSlot(x, z, offsetY + amount); }
        SurfaceSlot east() { return new SurfaceSlot(x + 1, z, offsetY); }
        SurfaceSlot west() { return new SurfaceSlot(x - 1, z, offsetY); }
        SurfaceSlot north() { return new SurfaceSlot(x, z - 1, offsetY); }
        SurfaceSlot south() { return new SurfaceSlot(x, z + 1, offsetY); }
    }
}
