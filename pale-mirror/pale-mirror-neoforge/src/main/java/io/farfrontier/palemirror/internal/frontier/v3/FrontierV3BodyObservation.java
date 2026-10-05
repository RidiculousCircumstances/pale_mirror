package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.Optional;

/** One conversion boundary for physical pedestrian poses, never a goal-arrival oracle. */
final class FrontierV3BodyObservation {
    /** Exact physical evidence only; neither a historical canonical pose nor a body owner. */
    private static final java.util.Map<Entity, DepartingContact> DEPARTING_CONTACTS = new java.util.WeakHashMap<>();
    private FrontierV3BodyObservation() { }

    static Observation capture(Entity body) {
        Objects.requireNonNull(body, "physical body");
        var support = body.getOnPos();
        if (body.level() instanceof ServerLevel level) {
            // hasChunkAt tests the ticket level, not the physical presence of the full
            // chunk. A hidden body's final save may precede the actual terrain unload.
            // Observe the available object without requesting a ticket or generation.
            var chunk = level.getChunkSource().getChunkNow(support.getX() >> 4, support.getZ() >> 4);
            if (chunk != null) return capture(body, chunk, support);
        }
        return unsupported(body);
    }

    private static Observation capture(Entity body, net.minecraft.world.level.BlockGetter geometry,
                                       net.minecraft.core.BlockPos support) {
        var collision = geometry.getBlockState(support).getCollisionShape(geometry, support);
        var bounds = body.getBoundingBox();
        var contactTop = collision.toAabbs().stream()
                .filter(box -> bounds.maxX > support.getX() + box.minX && bounds.minX < support.getX() + box.maxX
                        && bounds.maxZ > support.getZ() + box.minZ && bounds.minZ < support.getZ() + box.maxZ)
                .mapToDouble(box -> support.getY() + box.maxY).max();
        if (!collision.isEmpty() && geometry.getFluidState(support).isEmpty()
                && geometry.getFluidState(support.above()).isEmpty()
                && contactTop.isPresent() && Math.abs(bounds.minY - contactTop.orElseThrow()) <= 0.1D) {
            var surface = SurfaceAnchor.at(support.getX(), support.getY(), support.getZ());
            return new Observation(surface.standingBody(), Optional.of(surface));
        }
        return unsupported(body);
    }

    private static Observation unsupported(Entity body) {
        // A falling/swimming pose is a spatial cell, not evidence of a supporting block.
        return new Observation(new BodyPosition(body.getBlockX(), body.getBlockY(), body.getBlockZ()), Optional.empty());
    }

    /** ChunkMap supplies the final actual terrain before saving/unloading its geometry.
     * Entity sections may already be HIDDEN: global UUID lookup cannot enumerate them.
     * Keep only a primitive, object-bound contact; never retain a chunk or load one.
     */
    static void observeTerrainDeparture(Entity body, net.minecraft.world.level.chunk.LevelChunk chunk) {
        var support = body.getOnPos();
        if (body.level() != chunk.getLevel() || !new net.minecraft.world.level.ChunkPos(support).equals(chunk.getPos())) return;
        var observation = capture(body, chunk, support);
        if (observation.support().isPresent()) {
            DEPARTING_CONTACTS.put(body, new DepartingContact(body.getX(), body.getY(), body.getZ(), observation));
        } else DEPARTING_CONTACTS.remove(body);
    }

    /** Storage and final removal share the same witness, bound to the exact serialized pose.
     * A loaded column is always observed afresh: cached contact cannot hide changed terrain.
     * Motion after the terrain callback invalidates the witness, rather than rounding feet Y.
     */
    static Observation captureForDeparture(Entity body) {
        var support = body.getOnPos();
        var current = capture(body);
        if (!(body.level() instanceof ServerLevel level)
                || level.getChunkSource().getChunkNow(support.getX() >> 4, support.getZ() >> 4) != null) return current;
        var chunks = level.getChunkSource().chunkMap;
        long column = net.minecraft.world.level.ChunkPos.asLong(support);
        var holder = chunks.getVisibleChunkIfPresent(column);
        if (holder == null) holder = ((io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3UnloadingTerrainAccessor)
                chunks).frontierV3$getPendingUnloads().get(column);
        // A status downgrade hides FULL from getChunkNow, but the actual ready full
        // object remains vanilla's save source. This read never joins a future or loads.
        if (holder != null && holder.getLatestChunk() instanceof net.minecraft.world.level.chunk.LevelChunk unloading)
            return capture(body, unloading, support);
        var retained = DEPARTING_CONTACTS.get(body);
        return retained != null && retained.matches(body.getX(), body.getY(), body.getZ())
                ? retained.observation() : unsupported(body);
    }

    static void forgetDeparture(Entity body) { DEPARTING_CONTACTS.remove(body); }

    record DepartingContact(double x, double y, double z, Observation observation) {
        DepartingContact {
            Objects.requireNonNull(observation);
            if (observation.support().isEmpty()) throw new IllegalArgumentException("departure contact requires observed support");
        }
        boolean matches(double actualX, double actualY, double actualZ) {
            return Double.compare(x, actualX) == 0 && Double.compare(y, actualY) == 0 && Double.compare(z, actualZ) == 0;
        }
    }

    static BodyPosition position(Entity body) { return capture(body).position(); }

    /** GroundPathNavigation requires current contact even for a newly created NoAI body. */
    static void refreshGroundContact(ServerLevel level, net.minecraft.world.entity.Mob body) {
        body.setOnGround(!level.noCollision(body, body.getBoundingBox().move(0.0D, -0.01D, 0.0D)));
    }

    record Observation(BodyPosition position, Optional<SurfaceAnchor> support) {
        Observation {
            Objects.requireNonNull(position, "observed body position");
            Objects.requireNonNull(support, "observed support");
            if (support.isPresent() && !support.orElseThrow().standingBody().equals(position))
                throw new IllegalArgumentException("observed body disagrees with collision support");
        }

        Optional<BodyPosition> supportedBody() { return support.map(SurfaceAnchor::standingBody); }
    }
}
