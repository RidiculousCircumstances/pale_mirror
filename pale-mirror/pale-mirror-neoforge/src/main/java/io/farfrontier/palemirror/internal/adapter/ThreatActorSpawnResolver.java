package io.farfrontier.palemirror.internal.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Bounded deterministic placement for source-neutral PM encounter carriers. */
public final class ThreatActorSpawnResolver {
    private static final int MAX_HORIZONTAL_RADIUS = 6;
    private static final List<Integer> VERTICAL_OFFSETS = List.of(0, 1, -1, 2, -2, 3, -3);
    private static final List<BlockPos> HORIZONTAL_OFFSETS = horizontalOffsets();

    private ThreatActorSpawnResolver() { }

    public static Optional<Vec3> resolve(ServerLevel level, TestMineRecord mine, Mob actor, int slotIndex) {
        int offsetCount = HORIZONTAL_OFFSETS.size();
        int start = Math.floorMod(slotIndex * 17, offsetCount);
        for (int vertical : VERTICAL_OFFSETS) {
            for (int index = 0; index < offsetCount; index++) {
                BlockPos horizontal = HORIZONTAL_OFFSETS.get((start + index) % offsetCount);
                BlockPos feet = mine.anchor().offset(horizontal.getX(), 1 + vertical, horizontal.getZ());
                if (!mine.contains(feet) || !mine.contains(feet.above()) || !level.hasChunkAt(feet)) continue;
                BlockPos support = feet.below();
                if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) continue;
                if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) continue;
                Vec3 position = Vec3.atBottomCenterOf(feet);
                actor.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
                if (level.noCollision(actor, actor.getBoundingBox())) return Optional.of(position);
            }
        }
        return Optional.empty();
    }

    private static List<BlockPos> horizontalOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        for (int radius = 1; radius <= MAX_HORIZONTAL_RADIUS; radius++) {
            for (int x = -radius; x <= radius; x++) offsets.add(new BlockPos(x, 0, -radius));
            for (int z = -radius + 1; z <= radius; z++) offsets.add(new BlockPos(radius, 0, z));
            for (int x = radius - 1; x >= -radius; x--) offsets.add(new BlockPos(x, 0, radius));
            for (int z = radius - 1; z > -radius; z--) offsets.add(new BlockPos(-radius, 0, z));
        }
        return List.copyOf(offsets);
    }
}
