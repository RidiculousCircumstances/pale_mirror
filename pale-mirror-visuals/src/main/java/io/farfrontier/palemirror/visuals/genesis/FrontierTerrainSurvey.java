package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/** Generator-only access that keeps cheap biome ranking separate from explicitly budgeted exact heights. */
public final class FrontierTerrainSurvey {
    private static final int MINE_RISE_PREFILTER_MULTIPLIER = 6;
    public Batch selectBatch(ServerLevel level, RegionPlacementProfile profile, int count, int mapRadius,
                             int minimumSpacing) {
        return selectBatch(level, profile, count, 0, mapRadius, minimumSpacing);
    }

    public Batch selectBatch(ServerLevel level, RegionPlacementProfile profile, int count, int reserve,
                             int mapRadius, int minimumSpacing) {
        return selectBatch(level, profile, count, reserve, mapRadius, minimumSpacing, null);
    }

    public Batch selectBatch(ServerLevel level, RegionPlacementProfile profile, int count, int reserve,
                             int mapRadius, int minimumSpacing, DeterministicGenesisWorkers workers) {
        CachedLevelTerrain terrain = new CachedLevelTerrain(level, profile);
        BlockPos spawn = level.getSharedSpawnPos();
        List<FrontierSiteSelector.SelectedSite> selected = new FrontierSiteSelector(profile, workers).selectWithReserve(level.getSeed(),
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
        public SettlementLayoutResolver settlementLayouts() { return terrain::resolveSettlement; }
        public Statistics statistics() { return terrain.statistics(); }
    }

    public record Statistics(int cachedHeights, long heightHits, long heightMisses, long siteHeightProbes,
                             long mineHeightProbes, long railHeightProbes, long biomeSamples,
                             long discardedSiteCandidates) { }

    private static final class CachedLevelTerrain implements FrontierSiteSelector.TerrainAccess {
        private final ServerLevel level;
        private final RegionPlacementProfile profile;
        private final Map<Long, Integer> oceanFloors = new ConcurrentHashMap<>();
        private final Map<Long, Boolean> submergedColumns = new ConcurrentHashMap<>();
        private final Map<Long, FrontierSiteSelector.BiomeSample> biomes = new ConcurrentHashMap<>();
        private final LongAdder heightRequests = new LongAdder();
        private final LongAdder heightMisses = new LongAdder();
        private final LongAdder siteHeightProbes = new LongAdder();
        private final LongAdder mineHeightProbes = new LongAdder();
        private final LongAdder railHeightProbes = new LongAdder();
        private final LongAdder biomeSamples = new LongAdder();
        private final LongAdder discardedSiteCandidates = new LongAdder();

        private CachedLevelTerrain(ServerLevel level, RegionPlacementProfile profile) {
            this.level = level;
            this.profile = profile;
        }

        @Override public TerrainSample exactSample(int x, int z) {
            siteHeightProbes.increment();
            boolean water = exactWater(x, z);
            return new TerrainSample(x, z, oceanFloor(x, z), water);
        }

        @Override public boolean exactWater(int x, int z) {
            long key = ChunkPos.asLong(x, z);
            return submergedColumns.computeIfAbsent(key, ignored -> {
                int floor = oceanFloor(x, z);
                mineHeightProbes.increment();
                // Surface water in the Overworld is governed by the horizontal
                // biome field and sea-level fluid picker. A dry biome below sea
                // level is still conservatively rejected; underground aquifers
                // never turn a positive-density surface cell into water.
                return biome(x, z).water() || floor < level.getSeaLevel();
            });
        }

        @Override public FrontierSiteSelector.BiomeSample biome(int x, int z) {
            long key = ChunkPos.asLong(x, z);
            return biomes.computeIfAbsent(key, ignored -> FrontierBiomeClassifier.classify(rawBiome(x, z)));
        }

        @Override public void recordDiscardedCandidate(TerrainCandidate candidate) {
            discardedSiteCandidates.increment();
            io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod.LOGGER.debug(
                    "Discarded exact genesis site at {} {}: relief={}, waterSamples={}, cutFillCost={}, "
                            + "localGrade={}, roughness={}, depression={}, buildable={}%",
                    candidate.anchor().x(), candidate.anchor().z(), candidate.relief(), candidate.waterSamples(),
                    candidate.cutFillCost(), candidate.surfaceQuality().maximumLocalGrade(),
                    candidate.surfaceQuality().maximumRoughness(), candidate.surfaceQuality().depressionDepth(),
                    candidate.surfaceQuality().buildablePercent());
        }

        private MountainMineAnchor resolveMine(SitePlacementRequirement requirement, List<VisualPoint> candidates) {
            int exactAttempts = 0;
            java.util.Map<String, Integer> surfaceFailures = new java.util.LinkedHashMap<>();
            List<RankedMineCandidate> biomeRanked = java.util.stream.IntStream.range(0, candidates.size())
                    .mapToObj(index -> {
                        MineEdgeEvidence evidence = mineEdgeEvidence(candidates.get(index), requirement.terrain());
                        return new RankedMineCandidate(candidates.get(index), evidence.score(),
                                evidence.direction(), index);
                    })
                    .sorted(java.util.Comparator.comparingInt(RankedMineCandidate::evidence).reversed()
                            .thenComparingInt(RankedMineCandidate::index))
                    .toList();
            int prefilterBudget = Math.min(biomeRanked.size(), requirement.exactValidationBudget()
                    * MINE_RISE_PREFILTER_MULTIPLIER);
            List<RankedMineCandidate> exactRanked = biomeRanked.stream()
                    .filter(value -> FrontierTerrainSurvey.coarseDryFootprint(
                            value.point(), requirement.terrain(), this))
                    .limit(prefilterBudget)
                    .map(value -> value.withRise(quickMountainRise(value.point(), requirement.terrain(),
                            value.direction())))
                    .sorted(java.util.Comparator.comparingInt(RankedMineCandidate::rise).reversed()
                            .thenComparing(java.util.Comparator.comparingInt(
                                    RankedMineCandidate::evidence).reversed())
                            .thenComparingInt(RankedMineCandidate::index))
                    .toList();
            for (RankedMineCandidate ranked : exactRanked) {
                VisualPoint candidate = ranked.point();
                if (exactAttempts++ >= requirement.exactValidationBudget()) break;
                int terrainSurface = oceanFloor(candidate.x(), candidate.z());
                mineHeightProbes.increment();
                MountainFaceResolution face = mountainFace(candidate, terrainSurface, requirement.terrain());
                MountainMineAnchor mountain = face.anchor();
                if (mountain != null) {
                    if (!exactDryFootprint(candidate, requirement.terrain(), this)) {
                        surfaceFailures.merge("portal:water", 1, Integer::sum);
                        continue;
                    }
                    SurfaceResolution surface = resolveMineSurface(
                            requirement, mountain);
                    if (surface.centers() != null) return new MountainMineAnchor(mountain.portal(),
                            mountain.inwardQuarterTurns(), surface.centers());
                    surfaceFailures.merge(surface.failure(), 1, Integer::sum);
                } else {
                    surfaceFailures.merge("portal:" + face.failure(), 1, Integer::sum);
                }
            }
            throw new DryMineSiteUnavailableException("Cannot resolve a dry mountain MineSite within "
                    + requirement.exactValidationBudget() + " exact validation attempts for role "
                    + requirement.role() + (surfaceFailures.isEmpty() ? "" : "; surface=" + surfaceFailures));
        }

        /** Cheap biome-edge ranking; exact noise validation remains authoritative. */
        private MineEdgeEvidence mineEdgeEvidence(VisualPoint candidate, SiteTerrainPolicy policy) {
            FrontierSiteSelector.BiomeSample center = biome(candidate.x(), candidate.z());
            if (center.water()) return new MineEdgeEvidence(Integer.MIN_VALUE, 0);
            int best = Integer.MIN_VALUE;
            int bestDirection = 0;
            for (int direction = 0; direction < 4; direction++) {
                int dx = direction == 0 ? 1 : direction == 2 ? -1 : 0;
                int dz = direction == 1 ? 1 : direction == 3 ? -1 : 0;
                FrontierSiteSelector.BiomeSample apron = biome(candidate.x() - dx * policy.apronDistance(),
                        candidate.z() - dz * policy.apronDistance());
                if (apron.water()) continue;
                int score = apron.suitable() ? 4 : 1;
                if (!center.mountainEvidence()) score += 3;
                int ordinal = 0;
                for (SiteTerrainPolicy.RiseSample sample : policy.riseSamples()) {
                    FrontierSiteSelector.BiomeSample inward = biome(candidate.x() + dx * sample.distance(),
                            candidate.z() + dz * sample.distance());
                    if (inward.water()) score -= 8;
                    if (inward.mountainEvidence()) score += 4 + ordinal * 3;
                    ordinal++;
                }
                FrontierSiteSelector.BiomeSample far = biome(candidate.x() + dx
                                * policy.riseSamples().getLast().distance(),
                        candidate.z() + dz * policy.riseSamples().getLast().distance());
                if (far.mountainEvidence()) score += 8;
                if (score > best) {
                    best = score;
                    bestDirection = direction;
                }
            }
            return new MineEdgeEvidence(best, bestDirection);
        }

        /** Median far-rise prefilter; four-direction continuity and pad checks remain authoritative. */
        private int quickMountainRise(VisualPoint candidate, SiteTerrainPolicy policy, int direction) {
            int surface = oceanFloor(candidate.x(), candidate.z());
            mineHeightProbes.increment();
            int farthest = policy.riseSamples().getLast().distance();
            int dx = direction == 0 ? 1 : direction == 2 ? -1 : 0;
            int dz = direction == 1 ? 1 : direction == 3 ? -1 : 0;
            return crossSectionHeight(candidate, dx, dz, farthest) - surface;
        }

        private io.farfrontier.palemirror.api.AuthoredSettlementSitePlan resolveSettlement(
                String source, VisualPoint anchor, FrontierClimate climate, int freightDirection,
                TerrainCandidate candidate) {
            Map<Long, Integer> coarse = new java.util.LinkedHashMap<>();
            for (int right = -SettlementLayoutPlanner.MASTER_HALF_WIDTH;
                 right <= SettlementLayoutPlanner.MASTER_HALF_WIDTH;
                 right += SettlementTerrainSnapshot.GRID_STEP) {
                for (int inward = -SettlementLayoutPlanner.MASTER_HALF_LENGTH;
                     inward <= SettlementLayoutPlanner.MASTER_HALF_LENGTH;
                     inward += SettlementTerrainSnapshot.GRID_STEP) {
                    VisualPoint point = settlementLocal(anchor, right, inward, freightDirection);
                    coarse.put(SettlementTerrainSnapshot.key(point.x(), point.z()), oceanFloor(point.x(), point.z()));
                    siteHeightProbes.increment();
                }
            }
            SettlementTerrainSnapshot snapshot = new SettlementTerrainSnapshot(anchor, coarse, (x, z) -> {
                siteHeightProbes.increment();
                return oceanFloor(x, z);
            }, this::exactWater);
            snapshot.requireSuitable(profile.settlementTerrain());
            return new SettlementLayoutPlanner().plan(source, anchor, climate, freightDirection, candidate, snapshot);
        }

        private static VisualPoint settlementLocal(VisualPoint anchor, int right, int inward, int direction) {
            int dx = switch (Math.floorMod(direction, 4)) {
                case 0 -> inward;
                case 1 -> -right;
                case 2 -> -inward;
                default -> right;
            };
            int dz = switch (Math.floorMod(direction, 4)) {
                case 0 -> right;
                case 1 -> inward;
                case 2 -> -right;
                default -> -inward;
            };
            return new VisualPoint(anchor.x() + dx, anchor.y(), anchor.z() + dz);
        }

        private MountainFaceResolution mountainFace(VisualPoint candidate, int surface, SiteTerrainPolicy policy) {
            if (policy.feature() != SiteTerrainPolicy.Feature.MOUNTAIN_FACE) {
                throw new IllegalStateException("Unsupported site terrain feature " + policy.feature());
            }
            MountainMineAnchor best = null;
            int bestRise = Integer.MIN_VALUE;
            boolean terraceFound = false;
            boolean finalRiseFound = false;
            for (int direction = 0; direction < 4; direction++) {
                int dx = direction == 0 ? 1 : direction == 2 ? -1 : 0;
                int dz = direction == 1 ? 1 : direction == 3 ? -1 : 0;
                int apron = oceanFloor(candidate.x() - dx * policy.apronDistance(),
                        candidate.z() - dz * policy.apronDistance());
                mineHeightProbes.increment();
                boolean continuous = true;
                int rise = Integer.MIN_VALUE;
                for (SiteTerrainPolicy.RiseSample sample : policy.riseSamples()) {
                    int sampledHeight = crossSectionHeight(candidate, dx, dz, sample.distance());
                    rise = sampledHeight - surface;
                    continuous &= rise >= sample.minimumRise();
                }
                boolean terrace = Math.abs(apron - surface) <= policy.apronMaximumRelief();
                terraceFound |= terrace;
                finalRiseFound |= terrace && rise >= policy.riseSamples().getLast().minimumRise();
                if (terrace && continuous && rise > bestRise) {
                    bestRise = rise;
                    best = new MountainMineAnchor(new VisualPoint(candidate.x(), surface, candidate.z()), direction);
                }
            }
            if (best != null) return new MountainFaceResolution(best, "");
            return new MountainFaceResolution(null, !terraceFound ? "apron"
                    : !finalRiseFound ? "rise" : "continuity");
        }

        private int crossSectionHeight(VisualPoint candidate, int dx, int dz, int distance) {
            int perpendicularX = -dz;
            int perpendicularZ = dx;
            int[] samples = new int[3];
            int index = 0;
            for (int lateral : new int[]{-8, 0, 8}) {
                samples[index++] = oceanFloor(candidate.x() + dx * distance + perpendicularX * lateral,
                        candidate.z() + dz * distance + perpendicularZ * lateral);
                mineHeightProbes.increment();
            }
            java.util.Arrays.sort(samples);
            return samples[1];
        }

        private SurfaceResolution resolveMineSurface(SitePlacementRequirement requirement, MountainMineAnchor mine) {
            var role = requirement.role().equals(RegionPlacementProfiles.PRIMARY_MINE)
                    ? io.farfrontier.palemirror.api.AuthoredMineRole.PRIMARY
                    : io.farfrontier.palemirror.api.AuthoredMineRole.ALTERNATE;
            String failure = "no-coherent-yard";
            for (MineSurfaceLayout.YardOffset yard : MineSurfaceLayout.candidateYards()) {
                java.util.Map<String, VisualPoint> centers = new java.util.LinkedHashMap<>();
                java.util.List<VisualBounds> occupied = new java.util.ArrayList<>();
                boolean rejected = false;
                for (MineSurfaceLayout.Pad pad : MineSurfaceLayout.pads(role)) {
                    VisualPoint candidate = MineSurfaceLayout.center(
                            pad, mine.portal(), mine.inwardQuarterTurns(), yard);
                    SurfacePadResolution attempt = resolveSurfacePad(pad, candidate, mine.inwardQuarterTurns());
                    if (attempt.center() == null) {
                        failure = pad.id() + ":" + attempt.failure();
                        rejected = true;
                        break;
                    }
                    // Structural footprints may not intersect. Their grading aprons
                    // may meet and merge into one yard/path without invalidating the site.
                    if (occupied.stream().anyMatch(value -> overlaps(attempt.bounds(), value))) {
                        failure = pad.id() + ":overlap";
                        rejected = true;
                        break;
                    }
                    occupied.add(attempt.bounds());
                    centers.put(pad.id(), attempt.center());
                }
                if (!rejected) return new SurfaceResolution(java.util.Map.copyOf(centers), "");
            }
            return new SurfaceResolution(null, failure);
        }

        private SurfacePadResolution resolveSurfacePad(MineSurfaceLayout.Pad pad, VisualPoint candidate,
                                                       int direction) {
                var bounds = pad.bounds(candidate, direction);
                java.util.List<Integer> samples = new java.util.ArrayList<>();
                int minimumX = bounds.min().x() - MineSurfaceLayout.APRON;
                int maximumX = bounds.max().x() + MineSurfaceLayout.APRON;
                int minimumZ = bounds.min().z() - MineSurfaceLayout.APRON;
                int maximumZ = bounds.max().z() + MineSurfaceLayout.APRON;
                java.util.List<VisualPoint> sampleColumns = new java.util.ArrayList<>();
                for (int x : new int[]{minimumX, (minimumX + maximumX) / 2, maximumX}) {
                    for (int z : new int[]{minimumZ, (minimumZ + maximumZ) / 2, maximumZ}) {
                        int floor = oceanFloor(x, z);
                        mineHeightProbes.increment();
                        samples.add(floor);
                        sampleColumns.add(new VisualPoint(x, 0, z));
                    }
                }
                samples.sort(Integer::compareTo);
                int target = samples.get(samples.size() / 2);
                int minimum = samples.getFirst();
                int maximum = samples.getLast();
                if (maximum - minimum > MineSurfaceLayout.MAXIMUM_RELIEF) {
                    return new SurfacePadResolution(null, null, "relief");
                }
                if (maximum - target > MineSurfaceLayout.MAXIMUM_CUT) {
                    return new SurfacePadResolution(null, null, "cut");
                }
                if (target - minimum > MineSurfaceLayout.MAXIMUM_FILL) {
                    return new SurfacePadResolution(null, null, "fill");
                }
                // Base-column fluid inspection is substantially more expensive than
                // height sampling. Spend it only after the pad has passed relief,
                // cut and fill checks.
                for (VisualPoint column : sampleColumns) {
                    if (exactWater(column.x(), column.z())) {
                        return new SurfacePadResolution(null, null, "water");
                    }
                }
                // getBaseHeight returns first air. Surface modules and their
                // exact pads are anchored to the solid block below it.
                VisualPoint center = new VisualPoint(candidate.x(), target - 1, candidate.z());
                return new SurfacePadResolution(center, pad.bounds(center, direction), "");
        }

        private static boolean overlaps(VisualBounds first, VisualBounds second) {
            return first.min().x() <= second.max().x() && first.max().x() >= second.min().x()
                    && first.min().z() <= second.max().z() && first.max().z() >= second.min().z();
        }

        private record SurfacePadResolution(VisualPoint center, VisualBounds bounds, String failure) { }
        private record SurfaceResolution(java.util.Map<String, VisualPoint> centers, String failure) { }
        private record MountainFaceResolution(MountainMineAnchor anchor, String failure) { }
        private record MineEdgeEvidence(int score, int direction) { }
        private record RankedMineCandidate(VisualPoint point, int evidence, int direction, int index, int rise) {
            private RankedMineCandidate(VisualPoint point, int evidence, int direction, int index) {
                this(point, evidence, direction, index, Integer.MIN_VALUE);
            }

            private RankedMineCandidate withRise(int value) {
                return new RankedMineCandidate(point, evidence, direction, index, value);
            }
        }

        private List<VisualPoint> resolveRail(RoutePlacementRequirement requirement, VisualPoint from, VisualPoint to) {
            List<VisualPoint> horizontal = RailCorridorPathfinder.find(from, to, requirement,
                    (x, z) -> biome(x, z).water());
            int segments = horizontal.size() - 1;
            int elevation = to.y() - from.y();
            if (Math.abs(elevation) * requirement.horizontalBlocksPerVerticalBlock() > Math.max(1, segments)) {
                throw new DryMineSiteUnavailableException("Route grade exceeds one-in-"
                        + requirement.horizontalBlocksPerVerticalBlock());
            }
            List<VisualPoint> result = new java.util.ArrayList<>(horizontal.size());
            int wetRun = 0;
            for (int index = 0; index < horizontal.size(); index++) {
                VisualPoint point = horizontal.get(index);
                int progressed = segments == 0 ? 0 : (Math.abs(elevation) * index + segments / 2) / segments;
                VisualPoint graded = new VisualPoint(point.x(),
                        from.y() + Integer.signum(elevation) * progressed, point.z());
                int floor = oceanFloor(point.x(), point.z());
                boolean water = biome(point.x(), point.z()).water();
                int surface = water ? Math.max(floor, level.getSeaLevel()) : floor;
                railHeightProbes.increment();
                wetRun = water ? wetRun + 1 : 0;
                if (wetRun > requirement.maximumWaterSpan()) {
                    throw new DryMineSiteUnavailableException("Rail corridor water span exceeds "
                            + requirement.maximumWaterSpan() + " blocks at " + point.x() + "," + point.z());
                }
                if (water && graded.y() < surface + requirement.bridgeClearance()) {
                    throw new DryMineSiteUnavailableException("Rail bridge lacks " + requirement.bridgeClearance()
                            + " blocks of clearance at " + point.x() + "," + point.z());
                }
                result.add(graded);
            }
            return List.copyOf(result);
        }

        private int oceanFloor(int x, int z) {
            long key = ChunkPos.asLong(x, z);
            heightRequests.increment();
            return oceanFloors.computeIfAbsent(key, ignored -> {
                heightMisses.increment();
                return level.getChunkSource().getGenerator().getBaseHeight(x, z,
                        Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState());
            });
        }

        private Holder<Biome> rawBiome(int x, int z) {
            biomeSamples.increment();
            int sampleY = level.getSeaLevel() + 16;
            var generator = level.getChunkSource().getGenerator();
            return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(sampleY),
                    QuartPos.fromBlock(z), level.getChunkSource().randomState().sampler());
        }

        private Statistics statistics() {
            long misses = heightMisses.sum();
            return new Statistics(oceanFloors.size(), heightRequests.sum() - misses, misses,
                    siteHeightProbes.sum(), mineHeightProbes.sum(), railHeightProbes.sum(), biomeSamples.sum(),
                    discardedSiteCandidates.sum());
        }
    }

    /**
     * Cheap hydrology prefilter only. Biome names are useful for ranking, but
     * they are not authoritative mountain evidence in a modded world: Tectonic
     * and Terralith can produce a perfectly usable rock face inside a biome
     * whose registry path contains no "mountain" token. The bounded exact
     * mountain-face height profile remains the correctness gate.
     */
    static boolean coarseDryFootprint(VisualPoint candidate, SiteTerrainPolicy policy,
                                      FrontierSiteSelector.TerrainAccess terrain) {
        FrontierSiteSelector.BiomeSample center = terrain.biome(candidate.x(), candidate.z());
        if (policy.requireDryFootprint() && center.water()) return false;
        int extent = policy.footprintHalfExtent();
        for (int x = -extent; x <= extent; x += policy.biomeSampleStep()) {
            for (int z = -extent; z <= extent; z += policy.biomeSampleStep()) {
                FrontierSiteSelector.BiomeSample sample = terrain.biome(candidate.x() + x, candidate.z() + z);
                if (policy.requireDryFootprint() && sample.water()) return false;
            }
        }
        return true;
    }

    static boolean exactDryFootprint(VisualPoint candidate, SiteTerrainPolicy policy,
                                     FrontierSiteSelector.TerrainAccess terrain) {
        if (!policy.requireDryFootprint()) return true;
        int extent = policy.footprintHalfExtent();
        for (int[] offset : new int[][]{{0, 0}, {-extent, -extent}, {-extent, 0}, {-extent, extent},
                {0, -extent}, {0, extent}, {extent, -extent}, {extent, 0}, {extent, extent}}) {
            if (terrain.exactWater(candidate.x() + offset[0], candidate.z() + offset[1])) return false;
        }
        return true;
    }

}
