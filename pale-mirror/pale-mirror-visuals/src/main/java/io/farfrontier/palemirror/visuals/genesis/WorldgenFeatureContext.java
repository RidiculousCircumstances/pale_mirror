package io.farfrontier.palemirror.visuals.genesis;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/** C2ME-safe current-feature scope used by the WorldGenRegion write firewall. */
public final class WorldgenFeatureContext {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private WorldgenFeatureContext() { }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean place(Feature feature, FeatureConfiguration configuration, WorldGenLevel level,
                                ChunkGenerator generator, RandomSource random, BlockPos origin) {
        String id = BuiltInRegistries.FEATURE.getKey(feature).toString();
        WorldgenExclusionIndex.FeatureCategory category = classify(feature, id);
        if (!WorldgenExclusionIndex.allowFeatureOrigin(generator, category, id, origin)) return false;
        Context previous = CURRENT.get();
        CURRENT.set(new Context(generator, category));
        try {
            return feature.place(configuration, level, generator, random, origin);
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public static boolean allowWrite(BlockPos position, BlockState state) {
        Context context = CURRENT.get();
        return context == null || WorldgenExclusionIndex.allowWrite(
                context.generator(), context.category(), position, state);
    }

    private static WorldgenExclusionIndex.FeatureCategory classify(Feature<?> feature, String id) {
        if (feature instanceof FrontierWorldgenFeature) return WorldgenExclusionIndex.FeatureCategory.PM_AUTHORED;
        String path = id.substring(id.indexOf(':') + 1).toLowerCase(java.util.Locale.ROOT);
        if (path.equals("freeze_top_layer") || path.contains("spring") || path.contains("lake")
                || path.contains("ore") || path.contains("geode") || path.contains("dripstone")
                || path.contains("fossil") || path.contains("monster_room")) {
            return WorldgenExclusionIndex.FeatureCategory.PRESERVE;
        }
        if (path.equals("tree") || path.contains("tree") || path.contains("huge_red_mushroom")
                || path.contains("huge_brown_mushroom") || path.contains("bamboo")
                || path.contains("root_system")) return WorldgenExclusionIndex.FeatureCategory.TREE;
        if (path.contains("flower") || path.contains("random_patch") || path.contains("simple_block")
                || path.contains("vegetation_patch") || path.contains("vines")
                || path.contains("multiface_growth") || path.contains("block_column")) {
            return WorldgenExclusionIndex.FeatureCategory.SMALL_DECORATION;
        }
        if (path.contains("block_pile") || path.contains("forest_rock") || path.contains("ice_spike")
                || path.contains("blue_ice") || path.contains("iceberg") || path.equals("disk")
                || path.contains("desert_well")) return WorldgenExclusionIndex.FeatureCategory.SURFACE_FORMATION;
        return WorldgenExclusionIndex.FeatureCategory.UNKNOWN;
    }

    private record Context(ChunkGenerator generator, WorldgenExclusionIndex.FeatureCategory category) { }
}
