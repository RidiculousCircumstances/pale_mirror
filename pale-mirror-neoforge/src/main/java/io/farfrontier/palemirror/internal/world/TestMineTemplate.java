package io.farfrontier.palemirror.internal.world;

import java.util.ArrayList;
import java.util.List;

import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.StoryAudienceId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Versioned, intentionally small template. Placement fails rather than overwriting unknown terrain. */
public final class TestMineTemplate {
    public static final String VERSION = "test-mine-v2";
    private static final List<BlockPos> NODE_OFFSETS = List.of(
            new BlockPos(-2, 0, -2), new BlockPos(2, 0, -2),
            new BlockPos(-2, 0, 2), new BlockPos(2, 0, 2));

    private TestMineTemplate() { }

    public static TestMineRecord place(ServerLevel level, BlockPos anchor, WorldObjectId id, StoryAudienceId audience) {
        for (int x = -4; x <= 4; x++) for (int y = 0; y <= 4; y++) for (int z = -4; z <= 4; z++) {
            if (!level.isEmptyBlock(anchor.offset(x, y, z))) {
                throw new IllegalStateException("Test mine volume is not empty");
            }
        }
        List<MutableCell> cells = new ArrayList<>();
        for (int x = -4; x <= 4; x++) for (int y = 0; y <= 4; y++) for (int z = -4; z <= 4; z++) {
            boolean shell = Math.abs(x) == 4 || Math.abs(z) == 4 || y == 0 || y == 4;
            if (shell) level.setBlock(anchor.offset(x, y, z), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
        }
        level.setBlock(anchor.above(), Blocks.LANTERN.defaultBlockState(), 3);
        NODE_OFFSETS.forEach(offset -> addCell(level, cells, anchor.offset(offset), InfectionBiomeStage.NODE));
        addFloorBiomeCells(level, cells, anchor);
        addApexBiomeCells(level, cells, anchor);
        WorldObjectRegistryEntry object = new WorldObjectRegistryEntry(id, level.dimension().location().toString(), anchor,
                anchor.offset(-4, 0, -4), anchor.offset(4, 4, 4), "pale_mirror:test_mine", VERSION,
                WorldObjectLifecycle.REPRESENTED);
        return new TestMineRecord(object, audience, cells, null, EncounterRecord.none(), null);
    }

    private static void addFloorBiomeCells(ServerLevel level, List<MutableCell> cells, BlockPos anchor) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                BlockPos offset = new BlockPos(x, 0, z);
                if (NODE_OFFSETS.contains(offset)) continue;
                int radius = Math.max(Math.abs(x), Math.abs(z));
                InfectionBiomeStage stage = radius <= 1 ? InfectionBiomeStage.FOOTHOLD
                        : radius <= 2 ? InfectionBiomeStage.INFESTED : InfectionBiomeStage.SIEGE;
                addCell(level, cells, anchor.offset(offset), stage);
            }
        }
    }

    private static void addApexBiomeCells(ServerLevel level, List<MutableCell> cells, BlockPos anchor) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            addCell(level, cells, anchor.offset(x, 4, z), InfectionBiomeStage.APEX);
        }
        for (int offset : List.of(-2, 0, 2)) {
            addCell(level, cells, anchor.offset(-4, 2, offset), InfectionBiomeStage.APEX);
            addCell(level, cells, anchor.offset(4, 2, offset), InfectionBiomeStage.APEX);
            addCell(level, cells, anchor.offset(offset, 2, -4), InfectionBiomeStage.APEX);
            addCell(level, cells, anchor.offset(offset, 2, 4), InfectionBiomeStage.APEX);
        }
    }

    private static void addCell(ServerLevel level, List<MutableCell> cells, BlockPos position, InfectionBiomeStage stage) {
        BlockState state = level.getBlockState(position);
        String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        cells.add(new MutableCell(position, block, block, false, stage));
    }
}
