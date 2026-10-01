package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.Optional;

/** Physical placement provider: no queue, work phase, custody or semantic arrival authority. */
final class FrontierV3BodyPlacement {
    enum Reason { OUTSIDE_SCOPE, CHUNK_UNLOADED, ENTITY_STORAGE_PENDING, NO_EXACT_SUPPORT, SPACE_OCCUPIED, NO_CONNECTED_PATH }
    record Rejection(SurfaceAnchor surface, Reason reason) { }
    record Selection(Optional<SurfaceAnchor> surface, List<Rejection> rejections) {
        Selection { rejections = List.copyOf(rejections); }
    }
    private FrontierV3BodyPlacement() { }
    static Optional<SurfaceAnchor> select(ServerLevel level, Mob candidate, List<SurfaceAnchor> surfaces,
                                          FrontierV3NavigationScope scope,
                                          java.util.function.BiFunction<ServerLevel, BlockPos, BlockPos> standing) {
        return assess(level, candidate, surfaces, scope, standing).surface();
    }
    static Selection assess(ServerLevel level, Mob candidate, List<SurfaceAnchor> surfaces,
                             FrontierV3NavigationScope scope,
                             java.util.function.BiFunction<ServerLevel, BlockPos, BlockPos> standing) {
        if (surfaces.isEmpty()) throw new IllegalArgumentException("body placement needs an explicit zone");
        List<Rejection> rejected = new java.util.ArrayList<>();
        SurfaceAnchor origin = surfaces.getFirst();
        for (SurfaceAnchor surface : surfaces) {
            BlockPos support = new BlockPos(surface.x(), surface.y(), surface.z());
            if (!scope.permits(surface.support())) { rejected.add(new Rejection(surface, Reason.OUTSIDE_SCOPE)); continue; }
            if (!level.hasChunkAt(support)) { rejected.add(new Rejection(surface, Reason.CHUNK_UNLOADED)); continue; }
            if (!level.areEntitiesLoaded(ChunkPos.asLong(support))) { rejected.add(new Rejection(surface, Reason.ENTITY_STORAGE_PENDING)); continue; }
            BlockPos feet = standing.apply(level, support);
            if (feet == null || !feet.equals(support.above())) { rejected.add(new Rejection(surface, Reason.NO_EXACT_SUPPORT)); continue; }
            var point = FrontierV3SemanticMovement.point(level, surface);
            candidate.setPos(point.x, point.y, point.z);
            if (!available(level, candidate, candidate.getBoundingBox())) { rejected.add(new Rejection(surface, Reason.SPACE_OCCUPIED)); continue; }
            if (!surface.equals(origin)) {
                BlockPos target = new BlockPos(origin.x(), origin.y() + 1, origin.z());
                if (!level.hasChunkAt(target)) { rejected.add(new Rejection(surface, Reason.CHUNK_UNLOADED)); continue; }
                FrontierV3BodyObservation.refreshGroundContact(level, candidate);
                var path = candidate.getNavigation().createPath(target, 0);
                if (FrontierV3PhysicalPathPolicy.reject(level, path, scope).isPresent()) { rejected.add(new Rejection(surface, Reason.NO_CONNECTED_PATH)); continue; }
            }
            return new Selection(Optional.of(surface), rejected);
        }
        return new Selection(Optional.empty(), rejected);
    }
    static boolean available(ServerLevel level, Mob candidate, AABB volume) {
        return level.noCollision(candidate, volume) && level.getEntities(candidate, volume.inflate(0.001D),
                entity -> entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()).isEmpty();
    }
    static boolean occupied(ServerLevel level, net.minecraft.world.entity.EntityType<?> type, SurfaceAnchor surface) {
        AABB volume = type.getDimensions().makeBoundingBox(FrontierV3SemanticMovement.point(level, surface));
        return !level.getEntities((net.minecraft.world.entity.Entity) null, volume.inflate(0.001D),
                entity -> entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()).isEmpty();
    }
}
