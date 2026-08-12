package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;

/** Generator-only site survey. It samples terrain without loading prospective chunks. */
public final class FrontierTerrainSurvey {
    private static final int SAMPLE_RADIUS = 80;
    private static final int SAMPLE_STEP = 20;

    public Result select(ServerLevel level, int ordinal) {
        BlockPos spawn = level.getSharedSpawnPos();
        int minimum = ordinal == 0 ? 1_024 : 6_000;
        int span = ordinal == 0 ? 1_025 : 4_001;
        long address = mix(level.getSeed() ^ (long) ordinal * 0x9E3779B97F4A7C15L);
        int baseDistance = minimum + Math.floorMod((int) address, span);
        double baseAngle = Math.floorMod((int) (address >>> 32), 360) * Math.PI / 180.0;
        List<TerrainCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            double angle = baseAngle + index * (Math.PI * 2.0 / 32.0);
            int distance = baseDistance + ((index % 5) - 2) * 96;
            int x = spawn.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * distance);
            candidates.add(sample(level, x, z));
        }
        TerrainCandidate selected = candidates.stream().min(TerrainCandidate.ordering()).orElseThrow();
        FrontierClimate climate = climate(level, selected.anchor());
        return new Result(selected, climate);
    }

    private static TerrainCandidate sample(ServerLevel level, int centerX, int centerZ) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        List<TerrainSample> samples = new ArrayList<>();
        for (int dx = -SAMPLE_RADIUS; dx <= SAMPLE_RADIUS; dx += SAMPLE_STEP) {
            for (int dz = -SAMPLE_RADIUS; dz <= SAMPLE_RADIUS; dz += SAMPLE_STEP) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int height = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                        level, level.getChunkSource().randomState());
                boolean water = !generator.getBaseColumn(x, z, level, level.getChunkSource().randomState())
                        .getBlock(Math.max(level.getMinBuildHeight(), height - 1)).getFluidState().isEmpty();
                samples.add(new TerrainSample(x, z, height, water));
            }
        }
        return TerrainCandidate.evaluate(centerX, centerZ, samples);
    }

    private static FrontierClimate climate(ServerLevel level, VisualPoint point) {
        var generator = level.getChunkSource().getGenerator();
        var holder = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(point.x()),
                QuartPos.fromBlock(point.y()), QuartPos.fromBlock(point.z()), level.getChunkSource().randomState().sampler());
        if (holder.is(BiomeTags.IS_TAIGA) || holder.value().getBaseTemperature() < 0.3F) return FrontierClimate.COLD_TAIGA;
        ResourceKey<Biome> key = holder.unwrapKey().orElse(null);
        String path = key == null ? "" : key.location().getPath();
        if (path.contains("desert") || path.contains("badlands") || path.contains("savanna")) return FrontierClimate.DRY_ARID;
        return FrontierClimate.TEMPERATE;
    }

    private static long mix(long value) {
        value ^= value >>> 30; value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27; value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    public record Result(TerrainCandidate terrain, FrontierClimate climate) { }
}
