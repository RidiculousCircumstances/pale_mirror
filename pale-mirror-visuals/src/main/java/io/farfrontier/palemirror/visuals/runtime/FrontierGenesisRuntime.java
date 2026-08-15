package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.GenesisReadiness;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import io.farfrontier.palemirror.visuals.genesis.CompiledChunkSlice;
import io.farfrontier.palemirror.visuals.genesis.CompiledGenesisCatalog;
import io.farfrontier.palemirror.visuals.genesis.FrontierGenesisCompiler;
import io.farfrontier.palemirror.visuals.genesis.FrontierRegionBatchPlanner;
import io.farfrontier.palemirror.visuals.genesis.FrontierRegionPlanner;
import io.farfrontier.palemirror.visuals.genesis.FrontierTerrainSurvey;
import io.farfrontier.palemirror.visuals.genesis.DeterministicGenesisWorkers;
import io.farfrontier.palemirror.visuals.genesis.FrontierWorldgenFeature;
import io.farfrontier.palemirror.visuals.genesis.RegionCountRange;
import io.farfrontier.palemirror.visuals.genesis.RegionPlacementProfile;
import io.farfrontier.palemirror.visuals.genesis.RegionPlacementProfiles;
import io.farfrontier.palemirror.visuals.genesis.VisualGenesisAttachments;
import io.farfrontier.palemirror.visuals.genesis.WorldgenExclusionIndex;
import io.farfrontier.palemirror.visuals.resident.ResidentMaterializer;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Plans once, publishes immutable slices, finalizes late biome features, and observes completed worldgen. */
@EventBusSubscriber(modid = PaleMirrorVisualsMod.MOD_ID)
public final class FrontierGenesisRuntime {
    private static final int OBSERVATION_BUDGET = 8;
    private static final Map<ServerLevel, List<AuthoredRegionSeed>> PLANS = new IdentityHashMap<>();
    private static final Map<ServerLevel, CompletableFuture<CompiledGenesisCatalog>> PLANNING =
            new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<PendingChunk> PENDING_CHUNKS = new ConcurrentLinkedQueue<>();
    private static final AtomicReference<CompiledGenesisCatalog> CATALOG = new AtomicReference<>();
    private static final AtomicReference<GenesisReadiness> READINESS = new AtomicReference<>(GenesisReadiness.planning());
    private static final AtomicLong WORLDGEN_SLICES = new AtomicLong();
    private static final AtomicLong WORLDGEN_NANOS = new AtomicLong();
    private static final AtomicLong WORLDGEN_MAX_NANOS = new AtomicLong();
    private static final AtomicBoolean FIRST_STAMP_OBSERVED = new AtomicBoolean();
    private static volatile PlanningMetrics planningMetrics = PlanningMetrics.empty();
    private static ExecutorService plannerExecutor;
    private static final ResidentMaterializer RESIDENTS = new ResidentMaterializer();

    private FrontierGenesisRuntime() { }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        if (event.getServer() instanceof GameTestServer) {
            PaleMirrorVisualsMod.LOGGER.info("Skipping terrain genesis planning on the synthetic GameTest world");
            return;
        }
        ServerLevel level = event.getServer().overworld();
        VisualGenesisSavedData ledger = VisualGenesisSavedData.get(level);
        READINESS.set(new GenesisReadiness(GenesisReadiness.State.PLANNING, 0, "", "Planning authored regions"));
        plannerExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "PaleMirror-Genesis-Planner");
            thread.setDaemon(true);
            return thread;
        });
        CompletableFuture<CompiledGenesisCatalog> future = CompletableFuture.supplyAsync(() -> {
            List<AuthoredRegionSeed> plans = ledger.manifests().isEmpty() ? plan(level) : ledger.manifests();
            READINESS.set(new GenesisReadiness(GenesisReadiness.State.COMPILING, 0, "", "Compiling chunk-local catalog"));
            long compileStarted = System.nanoTime();
            CompiledGenesisCatalog catalog = new FrontierGenesisCompiler().compile(plans);
            validateFoundry(catalog);
            planningMetrics = planningMetrics.withCompileNanos(System.nanoTime() - compileStarted);
            return catalog;
        }, plannerExecutor);
        PLANNING.put(level, future);
        future.whenComplete((catalog, failure) -> event.getServer().execute(() -> {
            if (PLANNING.remove(level) != future) return;
            if (failure != null) {
                READINESS.set(GenesisReadiness.failed(rootMessage(failure)));
                PaleMirrorVisualsMod.LOGGER.error("Authored-region genesis failed", failure);
                return;
            }
            VisualGenesisSavedData current = VisualGenesisSavedData.get(level);
            current.pinManifests(catalog.manifests());
            WorldgenExclusionIndex.install(level.getChunkSource().getGenerator(), catalog.manifests());
            CATALOG.set(catalog);
            install(level, catalog.manifests());
            READINESS.set(new GenesisReadiness(GenesisReadiness.State.READY, catalog.version(), catalog.hash(), ""));
            PaleMirrorVisualsMod.LOGGER.info("PM genesis catalog {} ready with {} chunk slices",
                    catalog.hash().substring(0, 16), catalog.chunks().size());
        }));
        PaleMirrorVisualsMod.LOGGER.info("Authored-region planning started on a dedicated coordinator");
    }

    private static List<AuthoredRegionSeed> plan(ServerLevel level) {
        FrontierTerrainSurvey survey = new FrontierTerrainSurvey();
        RegionPlacementProfile profile = RegionPlacementProfiles.IRON_FRONTIER;
        FrontierRegionPlanner planner = new FrontierRegionPlanner(profile);
        long started = System.nanoTime();
        RegionCountRange counts = new RegionCountRange(VisualServerConfig.GENESIS_MINIMUM_REGIONS.get(),
                VisualServerConfig.GENESIS_TARGET_REGIONS.get(), VisualServerConfig.GENESIS_MAXIMUM_REGIONS.get());
        int optionalSlots = counts.maximum() - counts.minimum();
        int reserve = Math.min(profile.search().reserveCandidateCount() + optionalSlots,
                profile.search().maximumSurveyCandidates() - counts.minimum());
        int workerCount = VisualServerConfig.GENESIS_PLANNER_WORKERS.get();
        FrontierTerrainSurvey.Batch batch;
        FrontierRegionBatchPlanner.Result planned;
        try (DeterministicGenesisWorkers workers = new DeterministicGenesisWorkers(workerCount)) {
            batch = survey.selectBatch(level, profile, counts.minimum(), reserve,
                    VisualServerConfig.GENESIS_MAP_RADIUS.get(), VisualServerConfig.GENESIS_MINIMUM_SPACING.get(),
                    workers);
            planned = new FrontierRegionBatchPlanner().plan(level.getSeed(), counts,
                    profile, batch.sites(), planner, batch.mineAnchors(), batch.railPaths(), batch.settlementLayouts(),
                    VisualServerConfig.GENESIS_MINIMUM_SPACING.get(), workers);
        }
        List<AuthoredRegionSeed> manifests = planned.manifests();
        var statistics = batch.statistics();
        planningMetrics = new PlanningMetrics(manifests.size(), workerCount, System.nanoTime() - started,
                0L, statistics.cachedHeights(), statistics.heightHits(), statistics.heightMisses(),
                statistics.siteHeightProbes(), statistics.mineHeightProbes(), statistics.railHeightProbes(), statistics.biomeSamples(),
                statistics.discardedSiteCandidates(), planned.evaluatedRegionCandidates(),
                planned.speculativeRegionCandidates());
        PaleMirrorVisualsMod.LOGGER.info("Batch-planned {} authored regions (minimum {}, target {}, maximum {}, targetMet={}) "
                        + "in {} ms on {} workers using {} exact site probes, "
                        + "{} mine probes, {} rail probes, {} unique heights, {} discarded sites, "
                        + "{} rejected region candidates, {} spacing rejects, {} evaluated candidates, "
                        + "{} speculative candidates and {} biome samples",
                manifests.size(), counts.minimum(), counts.target(), counts.maximum(), planned.targetMet(),
                planningMetrics.elapsedNanos() / 1_000_000L, workerCount, statistics.siteHeightProbes(),
                statistics.mineHeightProbes(), statistics.railHeightProbes(), statistics.heightMisses(), statistics.discardedSiteCandidates(),
                planned.rejectedRegionCandidates(), planned.spacingRejectedCandidates(),
                planned.evaluatedRegionCandidates(), planned.speculativeRegionCandidates(), statistics.biomeSamples());
        return manifests;
    }

    private static void validateFoundry(CompiledGenesisCatalog catalog) {
        var engine = new io.farfrontier.palemirror.visuals.foundry.FoundryAuditEngine();
        for (AuthoredRegionSeed region : catalog.manifests()) {
            var report = engine.auditCompiled(catalog, region.planId());
            if (!report.passed()) {
                String defects = report.findings().stream().filter(value -> value.severity().failsGate())
                        .limit(8).map(value -> value.ruleId() + "@" + value.position()).toList().toString();
                throw new IllegalStateException(report.summary() + " " + defects);
            }
            long warnings = report.count(io.farfrontier.palemirror.api.FoundrySeverity.WARNING);
            PaleMirrorVisualsMod.LOGGER.info("{}; warnings={}, auditedCells={}, elapsedMs={}", report.summary(),
                    warnings, metric(report, "compiled.cells"), metric(report, "compiled.elapsed"));
        }
    }

    private static double metric(io.farfrontier.palemirror.api.FoundryAuditReport report, String id) {
        return report.metrics().stream().filter(value -> value.id().equals(id)).mapToDouble(
                io.farfrontier.palemirror.api.FoundryMetric::value).findFirst().orElse(0D);
    }

    private static void install(ServerLevel level, List<AuthoredRegionSeed> plans) {
        plans.forEach(seed -> {
            AuthoredVisualProvider.INSTANCE.markers().observe(seed);
            PaleMirrorVisualsMod.LOGGER.info(
                    "Installed authored manifest {} at {} {} {} (climate {}), depotCore {} {} {}, railhead {} {} {}, "
                            + "Mine17 portal {} {} {}, Red Valley portal {} {} {}",
                    seed.planId(), seed.anchor().x(), seed.anchor().y(), seed.anchor().z(), seed.climate(),
                    seed.settlementSite().depotFunctionalCore().x(), seed.settlementSite().depotFunctionalCore().y(),
                    seed.settlementSite().depotFunctionalCore().z(), seed.settlementSite().receivingRailhead().x(),
                    seed.settlementSite().receivingRailhead().y(), seed.settlementSite().receivingRailhead().z(),
                    seed.primaryMineSite().portal().x(), seed.primaryMineSite().portal().y(),
                    seed.primaryMineSite().portal().z(), seed.alternateMineSite().portal().x(),
                    seed.alternateMineSite().portal().y(), seed.alternateMineSite().portal().z());
        });
        PLANS.put(level, plans);
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        String stamp = event.getChunk().getExistingData(VisualGenesisAttachments.GENESIS_STAMP).orElse("");
        PENDING_CHUNKS.add(new PendingChunk(level, event.getChunk().getPos().toLong(), stamp));
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (!READINESS.get().ready()) return;
        int budget = OBSERVATION_BUDGET;
        while (budget-- > 0) {
            PendingChunk pending = PENDING_CHUNKS.poll();
            if (pending == null) break;
            observeLoadedChunk(pending);
        }
    }

    private static void observeLoadedChunk(PendingChunk pending) {
        ServerLevel level = pending.level();
        ChunkPos chunk = new ChunkPos(pending.chunk());
        if (!level.hasChunk(chunk.x, chunk.z)) return;
        CompiledChunkSlice expected = compiledChunk(pending.chunk());
        if (expected == null) return;
        if (!expected.stamp().equals(pending.stamp())) {
            PaleMirrorVisualsMod.LOGGER.error("Genesis stamp mismatch for chunk {}: expected {}, observed {}",
                    chunk, expected.stamp(), pending.stamp().isBlank() ? "ABSENT" : pending.stamp());
            return;
        }
        VisualGenesisSavedData ledger = VisualGenesisSavedData.get(level);
        ledger.observeChunk(pending.chunk(), pending.stamp());
        if (FIRST_STAMP_OBSERVED.compareAndSet(false, true)) PaleMirrorVisualsMod.LOGGER.info(
                "Observed first exact authored worldgen stamp {} in chunk {}", pending.stamp(), chunk);
        List<AuthoredRegionSeed> plans = PLANS.get(level);
        if (plans == null) return;
        for (AuthoredRegionSeed seed : plans) if (intersects(seed, chunk)) RESIDENTS.materialize(level, chunk, seed, ledger);
    }

    @SubscribeEvent
    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        GenesisReadiness readiness = READINESS.get();
        if (!readiness.ready()) player.connection.disconnect(Component.literal(
                "Pale Mirror world genesis is " + readiness.state() + ". Retry shortly. " + readiness.diagnostic()));
    }

    public static CompiledChunkSlice compiledChunk(long chunk) {
        CompiledGenesisCatalog catalog = CATALOG.get();
        return catalog == null ? null : catalog.chunk(chunk);
    }

    /** Immutable catalog exposure for read-only Foundry diagnostics. */
    public static CompiledGenesisCatalog compiledCatalog() { return CATALOG.get(); }

    public static GenesisReadiness readiness() { return READINESS.get(); }

    public static void recordWorldgenDuration(long nanos) {
        WORLDGEN_SLICES.incrementAndGet();
        WORLDGEN_NANOS.addAndGet(Math.max(0L, nanos));
        WORLDGEN_MAX_NANOS.accumulateAndGet(Math.max(0L, nanos), Math::max);
    }

    public static String performanceSummary() {
        long slices = WORLDGEN_SLICES.get();
        return "worldgenSlices=" + slices + ", worldgenAverageMs="
                + (slices == 0 ? 0D : WORLDGEN_NANOS.get() / 1_000_000D / slices)
                + ", worldgenMaxMs=" + WORLDGEN_MAX_NANOS.get() / 1_000_000D
                + ", pendingChunkObservations=" + PENDING_CHUNKS.size()
                + ", " + WorldgenExclusionIndex.metrics()
                + ", " + planningMetrics.summary();
    }

    private static boolean intersects(AuthoredRegionSeed seed, ChunkPos chunk) {
        return chunk.getMaxBlockX() >= seed.settlementBounds().min().x() - 2
                && chunk.getMinBlockX() <= seed.settlementBounds().max().x() + 2
                && chunk.getMaxBlockZ() >= seed.settlementBounds().min().z() - 2
                && chunk.getMinBlockZ() <= seed.settlementBounds().max().z() + 2;
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        PLANNING.values().forEach(future -> future.cancel(true));
        PLANNING.clear(); PLANS.clear(); PENDING_CHUNKS.clear(); CATALOG.set(null);
        READINESS.set(GenesisReadiness.planning());
        WORLDGEN_SLICES.set(0); WORLDGEN_NANOS.set(0); WORLDGEN_MAX_NANOS.set(0);
        FIRST_STAMP_OBSERVED.set(false);
        planningMetrics = PlanningMetrics.empty();
        if (plannerExecutor != null) plannerExecutor.shutdownNow();
        plannerExecutor = null;
        AuthoredVisualProvider.INSTANCE.resetRuntime();
        WorldgenExclusionIndex.clear();
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record PendingChunk(ServerLevel level, long chunk, String stamp) { }

    private record PlanningMetrics(int regions, int workers, long elapsedNanos, long compileNanos, int cachedHeights,
                                   long heightHits, long heightMisses, long siteHeightProbes,
                                   long mineHeightProbes, long railHeightProbes,
                                   long biomeSamples, long discardedSiteCandidates,
                                   int evaluatedRegionCandidates, int speculativeRegionCandidates) {
        private static PlanningMetrics empty() {
            return new PlanningMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        private PlanningMetrics withCompileNanos(long value) {
            return new PlanningMetrics(regions, workers, elapsedNanos, value, cachedHeights, heightHits, heightMisses,
                    siteHeightProbes, mineHeightProbes, railHeightProbes, biomeSamples, discardedSiteCandidates,
                    evaluatedRegionCandidates, speculativeRegionCandidates);
        }
        private String summary() {
            return "plannedRegions=" + regions + ", planningWorkers=" + workers
                    + ", planningMs=" + elapsedNanos / 1_000_000D
                    + ", compileMs=" + compileNanos / 1_000_000D
                    + ", terrainHeightMisses=" + heightMisses + ", terrainHeightHits=" + heightHits
                    + ", exactSiteHeightProbes=" + siteHeightProbes + ", exactMineHeightProbes=" + mineHeightProbes
                    + ", railHeightProbes=" + railHeightProbes + ", discardedSiteCandidates=" + discardedSiteCandidates
                    + ", terrainBiomeSamples=" + biomeSamples + ", cachedTerrainHeights=" + cachedHeights
                    + ", evaluatedRegionCandidates=" + evaluatedRegionCandidates
                    + ", speculativeRegionCandidates=" + speculativeRegionCandidates;
        }
    }
}
