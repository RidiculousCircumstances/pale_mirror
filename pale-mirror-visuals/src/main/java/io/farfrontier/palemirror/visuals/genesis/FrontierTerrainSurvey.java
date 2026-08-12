package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/** Generator-only cached access layer for the pure batch site selector. */
public final class FrontierTerrainSurvey {
    private static final int HEIGHT_GRID = 16;

    public Batch selectBatch(ServerLevel level, int count, int mapRadius, int minimumSpacing) {
        CachedLevelTerrain terrain = new CachedLevelTerrain(level);
        BlockPos spawn = level.getSharedSpawnPos();
        List<FrontierSiteSelector.SelectedSite> selected = new FrontierSiteSelector().select(level.getSeed(),
                new VisualPoint(spawn.getX(), spawn.getY(), spawn.getZ()), count, mapRadius, minimumSpacing, terrain);
        IntBinaryOperator surfaceHeight = (x, z) -> terrain.height(gridCenter(x), gridCenter(z));
        return new Batch(selected, surfaceHeight, terrain.statistics());
    }

    private static int gridCenter(int coordinate) {
        return Math.floorDiv(coordinate, HEIGHT_GRID) * HEIGHT_GRID + HEIGHT_GRID / 2;
    }

    public record Batch(List<FrontierSiteSelector.SelectedSite> sites, IntBinaryOperator surfaceHeight,
                        Statistics statistics) { }
    public record Statistics(int cachedHeights, long heightHits, long heightMisses, long biomeSamples) { }

    private static final class CachedLevelTerrain implements FrontierSiteSelector.TerrainAccess {
        private final ServerLevel level;
        private final Map<Long, Integer> heights = new HashMap<>();
        private final Map<Long, TerrainSample> samples = new HashMap<>();
        private long heightHits;
        private long heightMisses;
        private long biomeSamples;

        private CachedLevelTerrain(ServerLevel level) { this.level = level; }

        @Override public TerrainSample sample(int x, int z) {
            long key = ChunkPos.asLong(x, z);
            return samples.computeIfAbsent(key, ignored -> {
                int height = height(x, z);
                var biome = biome(x, height, z);
                boolean water = height <= level.getSeaLevel() || biome.is(BiomeTags.IS_OCEAN)
                        || biome.is(BiomeTags.IS_RIVER);
                return new TerrainSample(x, z, height, water);
            });
        }

        private int height(int x, int z) {
            long key = ChunkPos.asLong(x, z);
            Integer cached = heights.get(key);
            if (cached != null) { heightHits++; return cached; }
            heightMisses++;
            int height = level.getChunkSource().getGenerator().getBaseHeight(x, z,
                    Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
            heights.put(key, height);
            return height;
        }

        @Override public FrontierClimate climate(int x, int z) {
            int height = height(x, z);
            var holder = biome(x, height, z);
            if (holder.is(BiomeTags.IS_TAIGA) || holder.value().getBaseTemperature() < 0.3F) {
                return FrontierClimate.COLD_TAIGA;
            }
            ResourceKey<Biome> key = holder.unwrapKey().orElse(null);
            String path = key == null ? "" : key.location().getPath();
            if (path.contains("desert") || path.contains("badlands") || path.contains("savanna")) {
                return FrontierClimate.DRY_ARID;
            }
            return FrontierClimate.TEMPERATE;
        }

        private net.minecraft.core.Holder<Biome> biome(int x, int y, int z) {
            biomeSamples++;
            var generator = level.getChunkSource().getGenerator();
            return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
                    QuartPos.fromBlock(z), level.getChunkSource().randomState().sampler());
        }

        private Statistics statistics() {
            return new Statistics(heights.size(), heightHits, heightMisses, biomeSamples);
        }
    }
}
