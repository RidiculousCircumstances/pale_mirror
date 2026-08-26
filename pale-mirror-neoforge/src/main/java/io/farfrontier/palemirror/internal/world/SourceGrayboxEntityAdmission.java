package io.farfrontier.palemirror.internal.world;

import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * Custody boundary for the disposable source-graybox dimension.
 *
 * <p>The source simulation owns every non-player living actor represented in
 * this dimension.  Allowing ambient or mod-native mobs to enter would let an
 * unrelated Minecraft combat loop delete canonical residents or bioforms.
 * The source materializer attaches deterministic identity before admitting
 * its own Villagers and Zombies, so those exact carriers remain allowed.</p>
 */
public final class SourceGrayboxEntityAdmission {
    private SourceGrayboxEntityAdmission() { }

    /** True when a non-source mob must be denied before it can affect the source projection. */
    public static boolean rejects(ServerLevel level, Entity entity) {
        return rejects(Objects.requireNonNull(level, "level").dimension(), entity);
    }

    static boolean rejects(ResourceKey<Level> dimension, Entity entity) {
        return Objects.requireNonNull(dimension, "dimension").equals(SourceGrayboxWorldBoundary.DIMENSION)
                && Objects.requireNonNull(entity, "entity") instanceof Mob
                && !SourceGrayboxMaterializer.recognizesManagedEntity(entity);
    }
}
