package io.farfrontier.palemirror.internal.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.farfrontier.palemirror.domain.StoryAudienceId;
import io.farfrontier.palemirror.domain.WorldObjectId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Product MineSite: surface loading yard, readable entrance, descending drift and controller chamber. */
public final class CampaignMineSiteTemplate {
    public static final String VERSION = "campaign-mine-v1";
    private static final int DRIFT_LENGTH = 72;
    private static final int DEPTH = 18;

    private CampaignMineSiteTemplate() { }

    public static boolean isAreaLoaded(ServerLevel level, BlockPos surface) {
        return plan(surface).keySet().stream().allMatch(level::hasChunkAt);
    }

    public static Map<Long, String> captureBaseline(ServerLevel level, BlockPos surface) {
        Map<Long, String> baseline = new LinkedHashMap<>();
        for (BlockPos position : plan(surface).keySet()) {
            requireWritable(level, position);
            baseline.put(position.asLong(), level.getBlockState(position).toString());
        }
        return Map.copyOf(baseline);
    }

    public static TestMineRecord place(ServerLevel level, BlockPos surface, WorldObjectId id, StoryAudienceId audience,
                                       Map<Long, String> baseline) {
        Map<BlockPos, BlockState> plan = plan(surface);
        if (baseline.size() != plan.size()) throw new IllegalStateException("MineSite baseline is absent or incomplete");
        for (BlockPos position : plan.keySet()) {
            String expected = baseline.get(position.asLong());
            if (expected == null || !expected.equals(level.getBlockState(position).toString())) {
                throw new IllegalStateException("MineSite changed after planning at " + position.toShortString());
            }
            requireWritable(level, position);
        }
        plan.forEach((position, state) -> level.setBlock(position, state, 3));
        BlockPos chamber = chamber(surface);
        List<MutableCell> cells = mutableCells(level, chamber);
        WorldObjectRegistryEntry object = new WorldObjectRegistryEntry(id, level.dimension().location().toString(), chamber,
                surface.offset(-8, -DEPTH - 4, -8), surface.offset(8, 8, DRIFT_LENGTH + 8),
                "pale_mirror:campaign_mine", VERSION, WorldObjectLifecycle.REPRESENTED);
        return new TestMineRecord(object, audience, cells, null, EncounterRecord.none(), null);
    }

    public static boolean isMaterialized(ServerLevel level, BlockPos surface) {
        BlockPos chamber = chamber(surface);
        return level.getBlockState(surface.offset(-3, 1, 2)).is(Blocks.STRIPPED_SPRUCE_LOG)
                && level.getBlockState(surface.offset(3, 1, 2)).is(Blocks.STRIPPED_SPRUCE_LOG)
                && level.getBlockState(chamber.offset(-6, 0, -6)).is(Blocks.DEEPSLATE_BRICKS)
                && level.getBlockState(chamber.above()).is(Blocks.SOUL_LANTERN);
    }

    public static TestMineRecord observeExisting(ServerLevel level, BlockPos surface, WorldObjectId id,
                                                 StoryAudienceId audience) {
        if (!isMaterialized(level, surface)) throw new IllegalStateException("Campaign MineSite postcondition failed");
        BlockPos chamber = chamber(surface);
        return new TestMineRecord(new WorldObjectRegistryEntry(id, level.dimension().location().toString(), chamber,
                surface.offset(-8, -DEPTH - 4, -8), surface.offset(8, 8, DRIFT_LENGTH + 8),
                "pale_mirror:campaign_mine", VERSION, WorldObjectLifecycle.REPRESENTED), audience,
                mutableCells(level, chamber), null, EncounterRecord.none(), null);
    }

    private static Map<BlockPos, BlockState> plan(BlockPos surface) {
        Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        // Loading yard and unmistakable mine entrance.
        for (int x = -7; x <= 7; x++) for (int z = -6; z <= 1; z++) {
            plan.put(surface.offset(x, -1, z), Blocks.GRAVEL.defaultBlockState());
            for (int y = 0; y <= 4; y++) plan.put(surface.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        for (int x : List.of(-3, 3)) for (int y = 0; y <= 4; y++)
            plan.put(surface.offset(x, y, 2), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        for (int x = -3; x <= 3; x++) plan.put(surface.offset(x, 4, 2), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        plan.put(surface.offset(-2, 3, 2), Blocks.LANTERN.defaultBlockState());
        plan.put(surface.offset(2, 3, 2), Blocks.LANTERN.defaultBlockState());
        for (int x = -5; x <= 5; x++) plan.put(surface.offset(x, 0, -3), Blocks.SMOOTH_STONE.defaultBlockState());

        // Three-wide supported drift; it descends gradually instead of teleporting into a chamber.
        for (int z = 2; z <= DRIFT_LENGTH; z++) {
            int drop = Math.min(DEPTH, z / 4);
            BlockPos floor = surface.offset(0, -drop, z);
            for (int x = -2; x <= 2; x++) plan.put(floor.offset(x, -1, 0), Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            for (int x = -1; x <= 1; x++) for (int y = 0; y <= 3; y++) plan.put(floor.offset(x, y, 0), Blocks.AIR.defaultBlockState());
            if (z % 8 == 0) {
                for (int y = 0; y <= 3; y++) {
                    plan.put(floor.offset(-2, y, 0), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                    plan.put(floor.offset(2, y, 0), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                }
                for (int x = -2; x <= 2; x++) plan.put(floor.offset(x, 3, 0), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                plan.put(floor.above(2), Blocks.LANTERN.defaultBlockState());
            }
        }

        BlockPos chamber = chamber(surface);
        for (int x = -6; x <= 6; x++) for (int y = -1; y <= 6; y++) for (int z = -6; z <= 6; z++) {
            boolean shell = Math.abs(x) == 6 || Math.abs(z) == 6 || y == -1 || y == 6;
            plan.put(chamber.offset(x, y, z), shell ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        plan.put(chamber.above(), Blocks.SOUL_LANTERN.defaultBlockState());
        return plan;
    }

    private static List<MutableCell> mutableCells(ServerLevel level, BlockPos chamber) {
        List<MutableCell> cells = new ArrayList<>();
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            int radius = Math.max(Math.abs(x), Math.abs(z));
            InfectionBiomeStage stage = radius <= 1 ? InfectionBiomeStage.FOOTHOLD
                    : radius <= 3 ? InfectionBiomeStage.INFESTED : InfectionBiomeStage.SIEGE;
            addCell(level, cells, chamber.offset(x, -1, z), stage);
        }
        for (BlockPos offset : List.of(new BlockPos(-4, 0, -4), new BlockPos(4, 0, -4),
                new BlockPos(-4, 0, 4), new BlockPos(4, 0, 4))) addCell(level, cells, chamber.offset(offset), InfectionBiomeStage.NODE);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            addCell(level, cells, chamber.offset(x, 6, z), InfectionBiomeStage.APEX);
        return cells;
    }

    private static void addCell(ServerLevel level, List<MutableCell> cells, BlockPos position, InfectionBiomeStage stage) {
        String block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock()).toString();
        cells.add(new MutableCell(position, block, block, false, stage));
    }

    private static BlockPos chamber(BlockPos surface) { return surface.offset(0, -DEPTH, DRIFT_LENGTH); }

    private static void requireWritable(ServerLevel level, BlockPos position) {
        if (!level.hasChunkAt(position)) throw new IllegalStateException("MineSite chunk is not loaded at " + position.toShortString());
        BlockState current = level.getBlockState(position);
        boolean natural = current.isAir() || current.canBeReplaced() || !current.getFluidState().isEmpty()
                || current.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || current.is(net.minecraft.tags.BlockTags.DIRT) || current.is(net.minecraft.tags.BlockTags.SAND)
                || current.is(net.minecraft.tags.BlockTags.LOGS) || current.is(net.minecraft.tags.BlockTags.LEAVES)
                || current.is(Blocks.GRAVEL) || current.is(Blocks.SNOW_BLOCK) || current.is(Blocks.ICE);
        if (current.hasBlockEntity() || current.is(Blocks.BEDROCK) || !natural) {
            throw new IllegalStateException("MineSite conflict at " + position.toShortString());
        }
    }
}
