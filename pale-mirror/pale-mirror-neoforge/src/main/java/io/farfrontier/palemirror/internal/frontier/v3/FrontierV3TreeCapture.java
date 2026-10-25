package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.internal.forestry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Actual successful vanilla generation/growth supplies its own log set. No neighbour flood fill,
 * native terrain scan, runtime economy read or requested chunk load. */
public final class FrontierV3TreeCapture {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private FrontierV3TreeCapture() { }
    public static void generated(WorldGenLevel world, Set<BlockPos> positions) {
        if (!FrontierV3PhysicalWorld.isPhysical(world.getLevel()) || positions.isEmpty() || positions.size() > TreeBlocks.MAX_LOGS) return;
        var rules = FrontierV3WorldgenSettings.ruleset(); if (!rules.forestry().enabled()) return;
        LOGGER.debug("PMV3_FOREST_CAPTURE stage=successful-feature placements={} origin={}", positions.size(), positions.iterator().next());
        var logs = new HashMap<BlockPosition, BlockExtraction.Block>(); String species = null;
        for (var point : positions) {
            var chunk = loaded(world, point.getX() >> 4, point.getZ() >> 4);
            if (chunk == null) { LOGGER.warn("PMV3_FOREST_CAPTURE stage=unavailable reason=UNLOADED_LOG position={}", point); return; }
            BlockState actual = chunk.getBlockState(point);
            // Vanilla's trunk setter also records the dirtProvider write under
            // the root. Its placement set is not itself a biological log set.
            if (!actual.is(net.minecraft.tags.BlockTags.LOGS)) continue;
            var block = FrontierV3MinecraftBlockExtraction.describe(actual);
            var profile = rules.forestry().harvests().stream().filter(value -> value.logKind().equals(block.kind())).findFirst().orElse(null);
            if (profile == null || species != null && !species.equals(profile.species())) {
                LOGGER.warn("PMV3_FOREST_CAPTURE stage=unsupported reason=LOG_PROFILE block={} position={}", block, point); return;
            }
            species = profile.species(); logs.put(new BlockPosition(point.getX(), point.getY(), point.getZ()), block);
        }
        if (logs.isEmpty()) { LOGGER.warn("PMV3_FOREST_CAPTURE stage=unsupported reason=NO_LOGS"); return; }
        var origin = logs.keySet().stream().min(Comparator.comparingInt(BlockPosition::y).thenComparingInt(BlockPosition::x).thenComparingInt(BlockPosition::z)).orElseThrow();
        if (logs.keySet().stream().anyMatch(point -> Math.abs((long)point.x()-origin.x()) > 127
                || Math.abs((long)point.y()-origin.y()) > 127 || Math.abs((long)point.z()-origin.z()) > 127)) return;
        var tree = new TreeDescriptor(new TreeId(UUID.randomUUID()), 1, "minecraft:tree_feature", species, origin, TreeBlocks.capture(origin, logs));
        String configuration = rules.contentSha256();
        var prepared = new LinkedHashMap<net.minecraft.world.level.chunk.ChunkAccess, GeneratedForest>();
        for (var region : tree.footprint()) {
            var chunk = loaded(world, region.x(), region.z());
            if (chunk == null) { LOGGER.warn("PMV3_FOREST_CAPTURE stage=unavailable reason=UNLOADED_PART region={}", region); return; }
            var previous = chunk.getData(ForestRegistration.GENERATED).orElse(null);
            if (previous != null && (!previous.configuration().equals(configuration) || previous.seed() != world.getSeed()))
                throw new IllegalStateException("tree capture crosses foreign generation provenance");
            var owned = new ArrayList<TreeDescriptor>(previous == null ? List.of() : previous.trees());
            var links = new ArrayList<GeneratedForest.Link>(previous == null ? List.of() : previous.links());
            if (owned.size() >= 256 || links.size() >= 256) {
                com.mojang.logging.LogUtils.getLogger().warn("PMV3 forest capture unavailable: bounded source metadata full at {}", region); return;
            }
            if (region.equals(tree.owningRegion())) owned.add(tree);
            else links.add(new GeneratedForest.Link(tree.id(), tree.generation(), tree.owningRegion()));
            prepared.put(chunk, new GeneratedForest(configuration, world.getSeed(), region, owned, links));
        }
        prepared.forEach((chunk, facts) -> { chunk.setData(ForestRegistration.GENERATED, Optional.of(facts)); chunk.setUnsaved(true); });
        LOGGER.debug("PMV3_FOREST_CAPTURE stage=recorded tree={} logs={} region={}", tree.id(), logs.size(), tree.owningRegion());
        FrontierV3ForestryObservation.loaded(world.getLevel(), new net.minecraft.world.level.ChunkPos(tree.owningRegion().x(), tree.owningRegion().z()));
    }
    /** A live growth callback reads the already-loaded FULL chunk directly, never waits on an EMPTY-status future. */
    private static net.minecraft.world.level.chunk.ChunkAccess loaded(WorldGenLevel world, int x, int z) {
        return world instanceof net.minecraft.server.level.ServerLevel level ? level.getChunkSource().getChunkNow(x, z)
                : world.getChunk(x, z, ChunkStatus.EMPTY, false);
    }
}
