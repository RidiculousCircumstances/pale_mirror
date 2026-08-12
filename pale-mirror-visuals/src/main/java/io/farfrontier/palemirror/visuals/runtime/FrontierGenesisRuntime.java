package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import io.farfrontier.palemirror.visuals.genesis.FrontierChunkMaterializer;
import io.farfrontier.palemirror.visuals.genesis.FrontierRegionPlanner;
import io.farfrontier.palemirror.visuals.genesis.FrontierTerrainSurvey;
import io.farfrontier.palemirror.visuals.resident.ResidentMaterializer;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Fresh-world genesis: plans globally, writes only the naturally loaded chunk. */
@EventBusSubscriber(modid = PaleMirrorVisualsMod.MOD_ID)
public final class FrontierGenesisRuntime {
    private static final int REGION_COUNT = 3;
    private static final int HEIGHT_SAMPLE_GRID = 16;
    private static final Map<ServerLevel, List<AuthoredRegionSeed>> PLANS = new IdentityHashMap<>();
    private static final Map<ServerLevel, CompletableFuture<List<AuthoredRegionSeed>>> PLANNING =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final FrontierChunkMaterializer MATERIALIZER = new FrontierChunkMaterializer();
    private static final ResidentMaterializer RESIDENTS = new ResidentMaterializer();

    private FrontierGenesisRuntime() { }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        VisualGenesisSavedData ledger = VisualGenesisSavedData.get(level);
        if (!ledger.manifests().isEmpty()) {
            install(level, ledger.manifests());
            return;
        }
        CompletableFuture<List<AuthoredRegionSeed>> future = CompletableFuture.supplyAsync(() -> plan(level));
        PLANNING.put(level, future);
        future.whenComplete((plans, failure) -> event.getServer().execute(() -> {
            if (PLANNING.remove(level) != future) return;
            if (failure != null) {
                PaleMirrorVisualsMod.LOGGER.error("Authored-region planning failed", failure);
                return;
            }
            VisualGenesisSavedData current = VisualGenesisSavedData.get(level);
            if (!current.manifests().isEmpty()) {
                install(level, current.manifests());
                return;
            }
            current.pinManifests(plans);
            install(level, plans);
        }));
        PaleMirrorVisualsMod.LOGGER.info("Authored-region planning started in the background");
    }

    private static List<AuthoredRegionSeed> plan(ServerLevel level) {
        FrontierTerrainSurvey survey = new FrontierTerrainSurvey();
        FrontierRegionPlanner planner = new FrontierRegionPlanner();
        Map<Long, Integer> surfaceHeights = new HashMap<>();
        java.util.function.IntBinaryOperator surfaceHeight = (x, z) -> {
            int sampleX = Math.floorDiv(x, HEIGHT_SAMPLE_GRID) * HEIGHT_SAMPLE_GRID + HEIGHT_SAMPLE_GRID / 2;
            int sampleZ = Math.floorDiv(z, HEIGHT_SAMPLE_GRID) * HEIGHT_SAMPLE_GRID + HEIGHT_SAMPLE_GRID / 2;
            return surfaceHeights.computeIfAbsent(ChunkPos.asLong(sampleX, sampleZ),
                    ignored -> survey.surfaceHeight(level, sampleX, sampleZ));
        };
        return java.util.stream.IntStream.range(0, REGION_COUNT).mapToObj(ordinal -> {
            FrontierTerrainSurvey.Result site = survey.select(level, ordinal);
            return planner.plan(level.getSeed(), ordinal, site.terrain().anchor(), site.climate(), surfaceHeight);
        }).toList();
    }

    private static void install(ServerLevel level, List<AuthoredRegionSeed> plans) {
        plans.forEach(seed -> {
            AuthoredVisualProvider.INSTANCE.markers().observe(seed);
            PaleMirrorVisualsMod.LOGGER.info("Installed authored manifest {} at {} {} {} (climate {})", seed.planId(),
                    seed.anchor().x(), seed.anchor().y(), seed.anchor().z(), seed.climate());
        });
        PLANS.put(level, plans);
        for (AuthoredRegionSeed seed : plans) resumeLoadedChunks(level, seed);
    }

    private static void resumeLoadedChunks(ServerLevel level, AuthoredRegionSeed seed) {
        int minX = Math.floorDiv(seed.settlementBounds().min().x() - 2, 16);
        int maxX = Math.floorDiv(seed.settlementBounds().max().x() + 2, 16);
        int minZ = Math.floorDiv(seed.settlementBounds().min().z() - 2, 16);
        int maxZ = Math.floorDiv(seed.settlementBounds().max().z() + 2, 16);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (level.hasChunk(x, z)) materializeLoadedChunk(level, new ChunkPos(x, z));
        }
    }

    @SubscribeEvent
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ChunkPos chunk = event.getChunk().getPos();
        level.getServer().execute(() -> materializeLoadedChunk(level, chunk));
    }

    private static void materializeLoadedChunk(ServerLevel level, ChunkPos chunk) {
        if (!level.hasChunk(chunk.x, chunk.z)) return;
        List<AuthoredRegionSeed> plans = PLANS.get(level);
        if (plans == null) return;
        VisualGenesisSavedData ledger = VisualGenesisSavedData.get(level);
        for (AuthoredRegionSeed seed : plans) {
            if (!MATERIALIZER.intersects(seed, chunk)) continue;
            if (!ledger.completed(seed.planId(), chunk.toLong())) {
                MATERIALIZER.materialize(level, chunk, seed, ledger);
                ledger.complete(seed.planId(), chunk.toLong());
            }
            RESIDENTS.materialize(level, chunk, seed, ledger);
        }
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        PLANNING.values().forEach(future -> future.cancel(true));
        PLANNING.clear();
        PLANS.clear();
        AuthoredVisualProvider.INSTANCE.resetRuntime();
    }
}
