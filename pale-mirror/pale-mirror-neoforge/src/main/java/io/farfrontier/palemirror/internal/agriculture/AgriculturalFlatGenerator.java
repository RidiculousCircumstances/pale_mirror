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

/** Vanilla flat geometry by delegation, with an explicit multi-biome source. */
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
    }
    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> lookup, RandomState random, long seed) {
        return ChunkGeneratorStructureState.createForFlat(random, seed, biomeSource, java.util.stream.Stream.empty());
    }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        return flat.fillFromNoise(blender, random, structures, chunk);
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
        return flat.getBaseHeight(x, z, type, height, random);
    }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        return flat.getBaseColumn(x, z, height, random);
    }
    @Override public int getMinY() { return flat.getMinY(); }
    @Override public int getGenDepth() { return flat.getGenDepth(); }
    @Override public int getSeaLevel() { return flat.getSeaLevel(); }
    @Override public void addDebugScreenInfo(List<String> text, RandomState random, BlockPos position) {
        text.add("Pale Mirror agriculture: " + io.farfrontier.palemirror.frontier.v3.model.agriculture.GrayboxAgriculture.rules()
                .biomeAt(position.getX(), position.getZ()));
    }
}
