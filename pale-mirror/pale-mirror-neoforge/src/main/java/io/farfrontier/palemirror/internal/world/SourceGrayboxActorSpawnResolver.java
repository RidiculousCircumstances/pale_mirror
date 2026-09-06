package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded physical admission check for a source-owned actor body.
 *
 * <p>The canonical projection supplies the desired exact hand-off point. This
 * resolver never creates a second actor position model: it accepts that point
 * when it is physically legal, otherwise it searches a deterministic local
 * apron and returns the actually admitted location for immediate persistence
 * in the actor execution ledger. It never loads chunks, clears blocks, or
 * treats player geometry as removable.</p>
 */
final class SourceGrayboxActorSpawnResolver {
    private static final int MAX_HORIZONTAL_RADIUS = 7;
    private static final List<BlockPos> OFFSETS = offsets();

    private SourceGrayboxActorSpawnResolver() { }

    static Optional<Vec3> resolve(ServerLevel level, Mob actor, Vec3 desired, String stableActorId) {
        int count = OFFSETS.size();
        int start = Math.floorMod(stableActorId.hashCode(), count);
        for (int index = 0; index < count; index++) {
            // The exact source point gets first refusal.  A stable rotation of
            // the remaining alternatives makes simultaneous blocked actors
            // disperse deterministically instead of converging on one square.
            BlockPos offset = index == 0 ? BlockPos.ZERO : OFFSETS.get((start + index - 1) % count);
            Vec3 candidate = desired.add(offset.getX(), 0.0d, offset.getZ());
            BlockPos feet = BlockPos.containing(candidate);
            if (!level.hasChunkAt(feet) || !level.getWorldBorder().isWithinBounds(feet)) continue;
            // Graybox bodies intentionally hover one presentation block above
            // the coloured surface so they do not stand inside the ground
            // rectangles.  Its immutable flat footing is therefore at
            // GROUND_Y - 1 rather than directly below the no-gravity body.
            BlockPos support = new BlockPos(feet.getX(), ReferenceGrayboxLayout.GROUND_Y - 1, feet.getZ());
            if (!level.hasChunkAt(support) || !level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) continue;
            if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) continue;
            actor.setPos(candidate);
            AABB body = actor.getBoundingBox();
            if (!level.noCollision(actor, body) || occupied(level, actor, body)) continue;
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    private static boolean occupied(ServerLevel level, Entity self, AABB body) {
        // Text displays, labels and item-like presentation entities may share
        // a coordinate without having a physical body.  They are not a spawn
        // obstruction; only a live creature/player is relevant here.
        return !level.getEntities(self, body.inflate(0.001d), entity -> entity instanceof LivingEntity
                        && entity.isAlive() && !entity.isSpectator())
                .isEmpty();
    }

    private static List<BlockPos> offsets() {
        List<BlockPos> result = new ArrayList<>();
        for (int radius = 1; radius <= MAX_HORIZONTAL_RADIUS; radius++) {
            for (int x = -radius; x <= radius; x++) result.add(new BlockPos(x, 0, -radius));
            for (int z = -radius + 1; z <= radius; z++) result.add(new BlockPos(radius, 0, z));
            for (int x = radius - 1; x >= -radius; x--) result.add(new BlockPos(x, 0, radius));
            for (int z = radius - 1; z > -radius; z--) result.add(new BlockPos(-radius, 0, z));
        }
        return List.copyOf(result);
    }

}
