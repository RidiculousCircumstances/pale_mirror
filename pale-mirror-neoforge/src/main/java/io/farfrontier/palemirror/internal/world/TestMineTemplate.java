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
    public static final String VERSION = "test-mine-v1";

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
        for (BlockPos offset : List.of(new BlockPos(-2, 0, -2), new BlockPos(2, 0, -2), new BlockPos(-2, 0, 2), new BlockPos(2, 0, 2))) {
            BlockPos pos = anchor.offset(offset);
            BlockState state = level.getBlockState(pos);
            String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            cells.add(new MutableCell(pos, block, block, false));
        }
        WorldObjectRegistryEntry object = new WorldObjectRegistryEntry(id, level.dimension().location().toString(), anchor,
                anchor.offset(-4, 0, -4), anchor.offset(4, 4, 4), "pale_mirror:test_mine", VERSION,
                WorldObjectLifecycle.REPRESENTED);
        return new TestMineRecord(object, audience, cells, null, EncounterRecord.none(), null);
    }
}
