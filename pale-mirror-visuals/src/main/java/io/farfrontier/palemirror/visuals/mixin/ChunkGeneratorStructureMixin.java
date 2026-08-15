package io.farfrontier.palemirror.visuals.mixin;

import io.farfrontier.palemirror.visuals.genesis.WorldgenExclusionIndex;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorStructureMixin {
    @Redirect(method = "tryGenerateStructure", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/Structure;generate("
                    + "Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGenerator;"
                    + "Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/world/level/levelgen/RandomState;"
                    + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;J"
                    + "Lnet/minecraft/world/level/ChunkPos;ILnet/minecraft/world/level/LevelHeightAccessor;"
                    + "Ljava/util/function/Predicate;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;"))
    private StructureStart paleMirror$rejectSurfaceStructure(Structure structure, RegistryAccess registries,
                                                              ChunkGenerator generator, BiomeSource biomes,
                                                              RandomState randomState,
                                                              StructureTemplateManager templates, long seed,
                                                              ChunkPos chunk, int references,
                                                              LevelHeightAccessor heights,
                                                              Predicate<Holder<Biome>> biomePredicate) {
        StructureStart start = structure.generate(registries, generator, biomes, randomState, templates, seed,
                chunk, references, heights, biomePredicate);
        return start.isValid() && WorldgenExclusionIndex.rejectSurfaceStructure(generator, start.getBoundingBox())
                ? StructureStart.INVALID_START : start;
    }
}
