package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.visuals.PaleMirrorVisualsMod;
import io.farfrontier.palemirror.visuals.genesis.FrontierChunkMaterializer;
import io.farfrontier.palemirror.visuals.genesis.FrontierRegionPlanner;
import io.farfrontier.palemirror.visuals.genesis.FrontierTerrainSurvey;
import io.farfrontier.palemirror.visuals.resident.ResidentMaterializer;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
    private static final Map<ServerLevel, List<AuthoredRegionSeed>> PLANS = new IdentityHashMap<>();
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
        FrontierTerrainSurvey survey = new FrontierTerrainSurvey();
        FrontierRegionPlanner planner = new FrontierRegionPlanner();
        List<AuthoredRegionSeed> plans = java.util.stream.IntStream.range(0, REGION_COUNT).mapToObj(ordinal -> {
            FrontierTerrainSurvey.Result site = survey.select(level, ordinal);
            AuthoredRegionSeed seed = planner.plan(level.getSeed(), ordinal, site.terrain().anchor(), site.climate());
            return seed;
        }).toList();
        ledger.pinManifests(plans);
        install(level, plans);
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
        PLANS.clear();
        AuthoredVisualProvider.INSTANCE.resetRuntime();
    }
}
