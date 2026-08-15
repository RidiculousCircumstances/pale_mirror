package io.farfrontier.palemirror.visuals.mixin;

import io.farfrontier.palemirror.visuals.genesis.WorldgenFeatureContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ConfiguredFeature.class)
abstract class ConfiguredFeatureContextMixin {
    @Redirect(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/feature/Feature;place("
                    + "Lnet/minecraft/world/level/levelgen/feature/configurations/FeatureConfiguration;"
                    + "Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;"
                    + "Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean paleMirror$placeWithEnvironment(Feature<?> feature, FeatureConfiguration configuration,
                                                     WorldGenLevel level, ChunkGenerator generator,
                                                     RandomSource random, BlockPos origin) {
        return WorldgenFeatureContext.place(feature, configuration, level, generator, random, origin);
    }
}
