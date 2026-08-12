package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/** Generator-only access that keeps cheap biome ranking separate from explicitly budgeted exact heights. */
public final class FrontierTerrainSurvey {
    public Batch selectBatch(ServerLevel level, int count, int mapRadius, int minimumSpacing) {
        CachedLevelTerrain terrain = new CachedLevelTerrain(level);
        BlockPos spawn = level.getSharedSpawnPos();
        List<FrontierSiteSelector.SelectedSite> selected = new FrontierSiteSelector().select(level.getSeed(),
                new VisualPoint(spawn.getX(), spawn.getY(), spawn.getZ()), count, mapRadius, minimumSpacing, terrain);
        return new Batch(selected, terrain);
    }

    public static final class Batch {
        private final List<FrontierSiteSelector.SelectedSite> sites;
        private final CachedLevelTerrain terrain;

        private Batch(List<FrontierSiteSelector.SelectedSite> sites, CachedLevelTerrain terrain) {
            this.sites = List.copyOf(sites);
            this.terrain = terrain;
        }

        public List<FrontierSiteSelector.SelectedSite> sites() { return sites; }
        public IntBinaryOperator mineHeight() { return terrain::mineHeight; }
        public Statistics statistics() { return terrain.statistics(); }
    }

    public record Statistics(int cachedHeights, long heightHits, long heightMisses, long siteHeightProbes,
                             long mineHeightProbes, long biomeSamples, long discardedSiteCandidates) { }

    private static final class CachedLevelTerrain implements FrontierSiteSelector.TerrainAccess {
        private final ServerLevel level;
        private final Map<Long, Integer> heights = new HashMap<>();
        private final Map<Long, FrontierSiteSelector.BiomeSample> biomes = new HashMap<>();
        private long heightHits;
        private long heightMisses;
        private long siteHeightProbes;
        private long mineHeightProbes;
        private long biomeSamples;
        private long discardedSiteCandidates;

        private CachedLevelTerrain(ServerLevel level) { this.level = level; }

        @Override public TerrainSample exactSample(int x, int z) {
            siteHeightProbes++;
            int height = height(x, z);
            boolean water = biome(x, z).water();
            return new TerrainSample(x, z, height, water);
        }

        @Override public FrontierSiteSelector.BiomeSample biome(int x, int z) {
            long key = ChunkPos.asLong(x, z);
            return biomes.computeIfAbsent(key, ignored -> classify(rawBiome(x, z)));
        }

        @Override public void recordDiscardedCandidate(TerrainCandidate candidate) {
            discardedSiteCandidates++;
            io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod.LOGGER.debug(
                    "Discarded exact genesis site at {} {}: relief={}, waterSamples={}, cutFillCost={}",
                    candidate.anchor().x(), candidate.anchor().z(), candidate.relief(), candidate.waterSamples(),
                    candidate.cutFillCost());
        }

        private int mineHeight(int x, int z) {
            mineHeightProbes++;
            return height(x, z);
        }

        private int height(int x, int z) {
            long key = ChunkPos.asLong(x, z);
            Integer cached = heights.get(key);
            if (cached != null) {
                heightHits++;
                return cached;
            }
            heightMisses++;
            int height = level.getChunkSource().getGenerator().getBaseHeight(x, z,
                    Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
            heights.put(key, height);
            return height;
        }

        private Holder<Biome> rawBiome(int x, int z) {
            biomeSamples++;
            int sampleY = level.getSeaLevel() + 16;
            var generator = level.getChunkSource().getGenerator();
            return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(sampleY),
                    QuartPos.fromBlock(z), level.getChunkSource().randomState().sampler());
        }

        private static FrontierSiteSelector.BiomeSample classify(Holder<Biome> holder) {
            ResourceKey<Biome> key = holder.unwrapKey().orElse(null);
            String path = key == null ? "" : key.location().getPath();
            boolean water = holder.is(BiomeTags.IS_OCEAN) || holder.is(BiomeTags.IS_RIVER)
                    || path.contains("swamp") || path.contains("beach") || path.contains("shore");
            boolean hostileTerrain = path.contains("peak") || path.contains("slope") || path.contains("windswept")
                    || path.contains("mountain") || path.contains("jagged");
            boolean suitable = !water && !hostileTerrain;
            FrontierClimate climate;
            if (holder.is(BiomeTags.IS_TAIGA) || holder.value().getBaseTemperature() < 0.3F) {
                climate = FrontierClimate.COLD_TAIGA;
            } else {
                climate = path.contains("desert") || path.contains("badlands") || path.contains("savanna")
                        ? FrontierClimate.DRY_ARID : FrontierClimate.TEMPERATE;
            }
            int preference = path.contains("plains") || path.contains("savanna") || path.contains("desert")
                    || path.contains("badlands") ? 0 : path.contains("forest") || path.contains("taiga") ? 1 : 2;
            return new FrontierSiteSelector.BiomeSample(climate, suitable, water, preference);
        }

        private Statistics statistics() {
            return new Statistics(heights.size(), heightHits, heightMisses, siteHeightProbes,
                    mineHeightProbes, biomeSamples, discardedSiteCandidates);
        }
    }
}
