package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle;
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
        Objects.requireNonNull(level, "level");
        return rejects(level.dimension(), entity, FrontierV3ServerLifecycle.recognizesManagedCarrier(level, entity));
    }

    static boolean rejects(ResourceKey<Level> dimension, Entity entity) {
        return rejects(dimension, entity, false);
    }

    /** Package-visible seam: the event bridge supplies only a strict canonical V3 proof. */
    static boolean rejects(ResourceKey<Level> dimension, Entity entity, boolean verifiedV3Carrier) {
        return Objects.requireNonNull(dimension, "dimension").equals(SourceGrayboxWorldBoundary.DIMENSION)
                && Objects.requireNonNull(entity, "entity") instanceof Mob
                && !SourceGrayboxMaterializer.recognizesManagedEntity(entity)
                && !verifiedV3Carrier;
    }
}
