package io.farfrontier.palemirror.internal.agriculture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Vanilla flat geometry with a shared biome map and height-preserving surface palette. */
public final class AgriculturalFlatGenerator extends ChunkGenerator {
    public static final MapCodec<AgriculturalFlatGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FlatLevelGeneratorSettings.CODEC.fieldOf("settings").forGetter(value -> value.flat.settings()),
            AgriculturalBiomeSource.CODEC.fieldOf("biomes").forGetter(value -> (AgriculturalBiomeSource) value.biomeSource)
    ).apply(instance, AgriculturalFlatGenerator::new));
    private final FlatLevelSource flat;
    public AgriculturalFlatGenerator(FlatLevelGeneratorSettings settings, AgriculturalBiomeSource biomes) {
        super(biomes);
        flat = new FlatLevelSource(settings);
        if (settings.structureOverrides().isEmpty() || settings.structureOverrides().orElseThrow().size() != 0)
            throw new IllegalArgumentException("agricultural graybox requires explicit empty structures");
        if (settings.getLayers().isEmpty() || settings.getLayers().getLast() == null
                || !settings.getLayers().getLast().isSolid())
            throw new IllegalArgumentException("agricultural graybox requires a solid top layer");
    }
    public io.farfrontier.palemirror.frontier.v3.model.agriculture.TerritoryAgriculture agriculture() {
        return ((AgriculturalBiomeSource) biomeSource).agriculture();
    }
    private io.farfrontier.palemirror.frontier.v3.model.GrayboxTerrainPlan terrain(LevelHeightAccessor height) {
        return io.farfrontier.palemirror.frontier.v3.model.GrayboxTerrainPlan.forLayout(
                ((AgriculturalBiomeSource) biomeSource).worldLayout(), surfaceY(height));
    }
    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> lookup, RandomState random, long seed) {
        return ChunkGeneratorStructureState.createForFlat(random, seed, biomeSource, java.util.stream.Stream.empty());
    }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        return flat.fillFromNoise(blender, random, structures, chunk).thenApply(filled -> {
            int y = surfaceY(filled);
            var cursor = new BlockPos.MutableBlockPos();
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int worldX = filled.getPos().getMinBlockX() + x, worldZ = filled.getPos().getMinBlockZ() + z;
                var surface = AgriculturalSurface.at(agriculture(), worldX, worldZ);
                filled.setBlockState(cursor.set(worldX, y, worldZ), surface, false);
                filled.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG).update(x, y, z, surface);
                filled.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG).update(x, y, z, surface);
            }
            var content = terrain(filled);
            var region = new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region(filled.getPos().x, filled.getPos().z);
            // Bottom-up writes make both additions and excavations update vanilla heightmaps correctly.
            for (var entry : content.in(region).entrySet().stream().sorted(java.util.Comparator.comparingInt(value -> value.getKey().y())).toList()) {
                var p = entry.getKey(); var block = AgriculturalSurface.resolve(entry.getValue());
                filled.setBlockState(cursor.set(p.x(), p.y(), p.z()), block, false);
                filled.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG).update(p.x() & 15, p.y(), p.z() & 15, block);
                filled.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG).update(p.x() & 15, p.y(), p.z() & 15, block);
            }
            return filled;
        });
    }
    @Override public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {
        flat.buildSurface(region, structures, random, chunk);
    }
    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager manager,
                                       StructureManager structures, ChunkAccess chunk, GenerationStep.Carving carving) {
        flat.applyCarvers(region, seed, random, manager, structures, chunk, carving);
    }
    @Override public void spawnOriginalMobs(WorldGenRegion region) { flat.spawnOriginalMobs(region); }
    @Override public int getSpawnHeight(LevelHeightAccessor height) { return flat.getSpawnHeight(height); }
    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState random) {
        var positions = terrain(height).column(x, z);
        if (positions.isEmpty()) return flat.getBaseHeight(x, z, type, height, random);
        var column = getBaseColumn(x, z, height, random);
        for (int y = Math.min(height.getMaxBuildHeight() - 1, Math.max(surfaceY(height), positions.getLast().y()));
                y >= height.getMinBuildHeight(); y--)
            if (type.isOpaque().test(column.getBlock(y))) return y + 1;
        return height.getMinBuildHeight();
    }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        var column = flat.getBaseColumn(x, z, height, random);
        column.setBlock(surfaceY(height), AgriculturalSurface.at(agriculture(), x, z));
        var plan = terrain(height); var positions = plan.column(x, z);
        if (positions.isEmpty()) return column;
        // Vanilla's flat NoiseColumn ends at its initial top layer; hills require
        // a full-height column, rather than writing above that array's boundary.
        var blocks = new net.minecraft.world.level.block.state.BlockState[height.getHeight()];
        for (int i = 0; i < blocks.length; i++) blocks[i] = column.getBlock(height.getMinBuildHeight() + i);
        var result = new NoiseColumn(height.getMinBuildHeight(), blocks);
        for (var position : positions) result.setBlock(position.y(), AgriculturalSurface.resolve(plan.at(position).orElseThrow()));
        return result;
    }
    private int surfaceY(LevelHeightAccessor height) {
        return height.getMinBuildHeight() + Math.min(height.getHeight(), flat.settings().getLayers().size()) - 1;
    }
    @Override public int getMinY() { return flat.getMinY(); }
    @Override public int getGenDepth() { return flat.getGenDepth(); }
    @Override public int getSeaLevel() { return flat.getSeaLevel(); }
    @Override public void addDebugScreenInfo(List<String> text, RandomState random, BlockPos position) {
        text.add("Pale Mirror agriculture: " + agriculture()
                .biomeAt(position.getX(), position.getZ()));
    }
}
