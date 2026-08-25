package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Immutable dedicated-level identity and finite-border owner for the source graybox. */
final class SourceGrayboxWorldBoundary {
    static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(PaleMirrorMod.MOD_ID, "frontier_graybox"));

    private SourceGrayboxWorldBoundary() { }

    /**
     * The graybox never prepares terrain in the ordinary overworld. Its flat
     * data-driven level is a required disposable test boundary, so a missing
     * definition fails activation rather than projecting onto a player landscape.
     */
    static ServerLevel level(MinecraftServer server) {
        ServerLevel result = server.getLevel(DIMENSION);
        if (result == null) throw new IllegalStateException("source graybox dimension is unavailable");
        return result;
    }

    static void enforce(ServerLevel level) {
        level.getWorldBorder().setCenter(0.0d, 0.0d);
        level.getWorldBorder().setSize(ReferenceGrayboxLayout.WORLD_BLOCKS);
    }
}
