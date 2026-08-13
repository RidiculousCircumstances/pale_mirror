package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    public Batch selectBatch(ServerLevel level, RegionPlacementProfile profile, int count, int mapRadius,
                             int minimumSpacing) {
        return selectBatch(level, profile, count, 0, mapRadius, minimumSpacing);
    }

    public Batch selectBatch(ServerLevel level, RegionPlacementProfile profile, int count, int reserve,
                             int mapRadius, int minimumSpacing) {
        CachedLevelTerrain terrain = new CachedLevelTerrain(level);
        BlockPos spawn = level.getSharedSpawnPos();
        List<FrontierSiteSelector.SelectedSite> selected = new FrontierSiteSelector(profile).selectWithReserve(level.getSeed(),
                new VisualPoint(spawn.getX(), spawn.getY(), spawn.getZ()), count, reserve, mapRadius,
                minimumSpacing, terrain);
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
        public MineAnchorResolver mineAnchors() { return terrain::resolveMine; }
        public RailPathResolver railPaths() { return terrain::resolveRail; }
        public Statistics statistics() { return terrain.statistics(); }
    }

    public record Statistics(int cachedHeights, long heightHits, long heightMisses, long siteHeightProbes,
                             long mineHeightProbes, long railHeightProbes, long biomeSamples,
                             long discardedSiteCandidates) { }

    private static final class CachedLevelTerrain implements FrontierSiteSelector.TerrainAccess {
        private final ServerLevel level;
        private final Map<Long, Integer> heights = new HashMap<>();
        private final Map<Long, Integer> oceanFloors = new HashMap<>();
        private final Map<Long, FrontierSiteSelector.BiomeSample> biomes = new HashMap<>();
        private long heightHits;
        private long heightMisses;
        private long siteHeightProbes;
        private long mineHeightProbes;
        private long railHeightProbes;
        private long biomeSamples;
        private long discardedSiteCandidates;

        private CachedLevelTerrain(ServerLevel level) { this.level = level; }

        @Override public TerrainSample exactSample(int x, int z) {
            siteHeightProbes++;
            boolean water = biome(x, z).water();
            int terrainSurface = water ? height(x, z) : oceanFloor(x, z);
            return new TerrainSample(x, z, terrainSurface, water);
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

        private MountainMineAnchor resolveMine(SitePlacementRequirement requirement, List<VisualPoint> candidates) {
            int exactAttempts = 0;
            for (VisualPoint candidate : candidates) {
                if (!FrontierTerrainSurvey.siteFootprint(candidate, requirement.terrain(), this)) continue;
                if (exactAttempts++ >= requirement.exactValidationBudget()) break;
                int terrainSurface = oceanFloor(candidate.x(), candidate.z());
                mineHeightProbes++;
                MountainMineAnchor mountain = mountainFace(candidate, terrainSurface, requirement.terrain());
                if (mountain != null) return mountain;
            }
            throw new DryMineSiteUnavailableException("Cannot resolve a dry mountain MineSite within "
                    + requirement.exactValidationBudget() + " exact validation attempts for role "
                    + requirement.role());
        }

        private MountainMineAnchor mountainFace(VisualPoint candidate, int surface, SiteTerrainPolicy policy) {
            if (policy.feature() != SiteTerrainPolicy.Feature.MOUNTAIN_FACE) {
                throw new IllegalStateException("Unsupported site terrain feature " + policy.feature());
            }
            MountainMineAnchor best = null;
            int bestRise = Integer.MIN_VALUE;
            for (int direction = 0; direction < 4; direction++) {
                int dx = direction == 0 ? 1 : direction == 2 ? -1 : 0;
                int dz = direction == 1 ? 1 : direction == 3 ? -1 : 0;
                int deepestDistance = policy.riseSamples().getLast().distance();
                FrontierSiteSelector.BiomeSample mountainBiome = biome(candidate.x() + dx * deepestDistance,
                        candidate.z() + dz * deepestDistance);
                if (!mountainBiome.mountainEvidence()) continue;
                int apron = oceanFloor(candidate.x() - dx * policy.apronDistance(),
                        candidate.z() - dz * policy.apronDistance());
                mineHeightProbes++;
                boolean continuous = true;
                int rise = Integer.MIN_VALUE;
                for (SiteTerrainPolicy.RiseSample sample : policy.riseSamples()) {
                    int sampledHeight = oceanFloor(candidate.x() + dx * sample.distance(),
                            candidate.z() + dz * sample.distance());
                    mineHeightProbes++;
                    rise = sampledHeight - surface;
                    continuous &= rise >= sample.minimumRise();
                }
                boolean terrace = Math.abs(apron - surface) <= policy.apronMaximumRelief();
                if (terrace && continuous && rise > bestRise) {
                    bestRise = rise;
                    best = new MountainMineAnchor(new VisualPoint(candidate.x(), surface, candidate.z()), direction);
                }
            }
            return best;
        }

        private List<VisualPoint> resolveRail(RoutePlacementRequirement requirement, VisualPoint from, VisualPoint to) {
            int dx = to.x() - from.x();
            int dz = to.z() - from.z();
            boolean alongX = Math.abs(dx) >= Math.abs(dz);
            List<VisualPoint> controls = new java.util.ArrayList<>();
            controls.add(from);
            int denominator = requirement.intermediateControlCount() + 1;
            for (int part = 1; part <= requirement.intermediateControlCount(); part++) {
                double fraction = part / (double) denominator;
                int baseX = (int) Math.round(from.x() + dx * fraction);
                int baseZ = (int) Math.round(from.z() + dz * fraction);
                VisualPoint best = null;
                long bestCost = Long.MAX_VALUE;
                for (int offset : requirement.lateralControlOffsets()) {
                    int x = baseX + (alongX ? 0 : offset);
                    int z = baseZ + (alongX ? offset : 0);
                    FrontierSiteSelector.BiomeSample biome = biome(x, z);
                    if (requirement.rejectWater() && biome.water()) continue;
                    int surface = oceanFloor(x, z);
                    railHeightProbes++;
                    int expected = (int) Math.round(from.y() + (to.y() - from.y()) * fraction);
                    long cost = Math.abs(surface - expected) * 16L + Math.abs(offset);
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = new VisualPoint(x, expected, z);
                    }
                }
                if (best == null) throw new DryMineSiteUnavailableException("Rail corridor crosses water at "
                        + baseX + "," + baseZ);
                controls.add(best);
            }
            controls.add(to);
            List<VisualPoint> horizontal = new java.util.ArrayList<>();
            for (int index = 1; index < controls.size(); index++) {
                List<VisualPoint> leg = FrontierRegionPlanner.cardinalRail(controls.get(index - 1), controls.get(index), 0);
                if (!horizontal.isEmpty()) leg = leg.subList(1, leg.size());
                horizontal.addAll(leg);
            }
            int segments = horizontal.size() - 1;
            int elevation = to.y() - from.y();
            if (Math.abs(elevation) * requirement.horizontalBlocksPerVerticalBlock() > Math.max(1, segments)) {
                throw new DryMineSiteUnavailableException("Route grade exceeds one-in-"
                        + requirement.horizontalBlocksPerVerticalBlock());
            }
            List<VisualPoint> result = new java.util.ArrayList<>(horizontal.size());
            for (int index = 0; index < horizontal.size(); index++) {
                VisualPoint point = horizontal.get(index);
                int progressed = segments == 0 ? 0 : (Math.abs(elevation) * index + segments / 2) / segments;
                result.add(new VisualPoint(point.x(), from.y() + Integer.signum(elevation) * progressed, point.z()));
            }
            return List.copyOf(result);
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

        private int oceanFloor(int x, int z) {
            long key = ChunkPos.asLong(x, z);
            Integer cached = oceanFloors.get(key);
            if (cached != null) {
                heightHits++;
                return cached;
            }
            heightMisses++;
            int height = level.getChunkSource().getGenerator().getBaseHeight(x, z,
                    Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState());
            oceanFloors.put(key, height);
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
            return new FrontierSiteSelector.BiomeSample(climate, suitable, water, preference, hostileTerrain);
        }

        private Statistics statistics() {
            return new Statistics(heights.size() + oceanFloors.size(), heightHits, heightMisses, siteHeightProbes,
                    mineHeightProbes, railHeightProbes, biomeSamples, discardedSiteCandidates);
        }
    }

    static boolean siteFootprint(VisualPoint candidate, SiteTerrainPolicy policy,
                                 FrontierSiteSelector.TerrainAccess terrain) {
        FrontierSiteSelector.BiomeSample center = terrain.biome(candidate.x(), candidate.z());
        if (policy.requireDryFootprint() && center.water()) return false;
        boolean mountain = center.mountainEvidence();
        int extent = policy.footprintHalfExtent();
        for (int x = -extent; x <= extent; x += policy.biomeSampleStep()) {
            for (int z = -extent; z <= extent; z += policy.biomeSampleStep()) {
                FrontierSiteSelector.BiomeSample sample = terrain.biome(candidate.x() + x, candidate.z() + z);
                if (policy.requireDryFootprint() && sample.water()) return false;
                mountain |= sample.mountainEvidence();
            }
        }
        if (mountain) return true;
        for (int distance : policy.nearbyEvidenceDistances()) for (int direction = 0; direction < 4; direction++) {
            int dx = direction == 0 ? distance : direction == 2 ? -distance : 0;
            int dz = direction == 1 ? distance : direction == 3 ? -distance : 0;
            if (terrain.biome(candidate.x() + dx, candidate.z() + dz).mountainEvidence()) return true;
        }
        return false;
    }

}
