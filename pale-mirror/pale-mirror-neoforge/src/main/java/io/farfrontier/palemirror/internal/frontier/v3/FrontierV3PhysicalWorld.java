package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** The one physical world allowed to represent the disposable Frontier v3 graybox. */
public final class FrontierV3PhysicalWorld {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(PaleMirrorMod.MOD_ID, "frontier_graybox"));
    static final WorldId WORLD_ID = new WorldId("frontier:graybox");

    private FrontierV3PhysicalWorld() { }

    public static ServerLevel require(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        ServerLevel level = server.getLevel(DIMENSION);
        if (level == null) throw new IllegalStateException("Frontier v3 graybox dimension is unavailable");
        return level;
    }

    public static boolean isPhysical(ServerLevel level) {
        return isPhysicalDimension(Objects.requireNonNull(level, "level").dimension());
    }

    static boolean isPhysicalDimension(ResourceKey<Level> dimension) {
        return DIMENSION.equals(Objects.requireNonNull(dimension, "dimension"));
    }
}
