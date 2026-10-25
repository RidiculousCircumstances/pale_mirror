package io.farfrontier.palemirror.internal.agriculture;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.model.agriculture.GrayboxAgriculture;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.flat.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AgriculturalSurfaceGameTests {
    @GameTest(batch = "pm-frontier-v3-graybox", templateNamespace = "minecraft",
            template = "bastion/mobs/empty", timeoutTicks = 40)
    public static void generatedSurfaceMatchesUnloadedColumnsWithoutChangingHeights(GameTestHelper helper) {
        verify(helper, "frontier-v3-quarry-graybox-r25", List.of(new ChunkPos(8, 0), new ChunkPos(-8, 0), new ChunkPos(0, 32), new ChunkPos(14, 10)));
        verify(helper, "frontier-v3-quarry-graybox-r27", List.of(new ChunkPos(32, -4), new ChunkPos(-33, 0), new ChunkPos(0, 100)));
        helper.succeed();
    }
    private static void verify(GameTestHelper helper, String profile, List<ChunkPos> positions) {
        var level = helper.getLevel();
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        var ordinary = registry.getHolderOrThrow(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(GrayboxAgriculture.ORDINARY)));
        var source = new AgriculturalBiomeSource(
                registry.getHolderOrThrow(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(GrayboxAgriculture.FERTILE))), ordinary,
                registry.getHolderOrThrow(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(GrayboxAgriculture.ROCKY))), profile);
        var settings = new FlatLevelGeneratorSettings(Optional.of(HolderSet.direct()), ordinary, List.of());
        settings.getLayersInfo().addAll(List.of(new FlatLayerInfo(1, Blocks.BEDROCK),
                new FlatLayerInfo(126, Blocks.STONE), new FlatLayerInfo(1, Blocks.LIGHT_GRAY_CONCRETE)));
        settings.updateLayers();
        var generator = new AgriculturalFlatGenerator(settings, source);
        var random = level.getChunkSource().randomState();
        var seen = new HashSet<net.minecraft.world.level.block.Block>();
        // Only detached protochunks are generated. No fixture/world cells or tickets are changed.
        for (var position : positions) {
            var chunk = new ProtoChunk(position, UpgradeData.EMPTY, level, registry, null);
            generator.fillFromNoise(Blender.empty(), random, level.structureManager(), chunk).join();
            int y = level.getMinBuildHeight() + 127;
            for (int x = position.getMinBlockX(); x <= position.getMaxBlockX(); x++)
                for (int z = position.getMinBlockZ(); z <= position.getMaxBlockZ(); z++) {
                    var actual = chunk.getBlockState(new BlockPos(x, y, z));
                    seen.add(actual.getBlock());
                    helper.assertTrue(actual.equals(AgriculturalSurface.at(source.agriculture(), x, z)), "generated palette follows shared biome map");
                    var declared = source.agriculture().surfaceAt(x, z).orElseThrow();
                    helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(actual.getBlock())
                            .toString().equals(declared.kind()), "unloaded land discovery uses the actual generated material");
                    var column = generator.getBaseColumn(x, z, level, random);
                    helper.assertTrue(actual.equals(column.getBlock(y)), "unloaded geometry sees the same native surface");
                    helper.assertTrue(column.getBlock(y - 1).is(Blocks.STONE) && column.getBlock(y + 1).isAir(),
                            "surface palette cannot change subsurface geology or standing height");
                    helper.assertTrue(generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random) == y + 1,
                            "flat base height remains unchanged");
                }
        }
        helper.assertTrue(seen.containsAll(List.of(Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.COARSE_DIRT)), "all visual surfaces exercised");
    }
}
