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
            if (chunk == null) chunk = level.getChunkSource().getChunkNow(body.getBlockX() >> 4, body.getBlockZ() >> 4);
            if (chunk != null) {
                var observation = capture(body, chunk, support);
                if (observation.support().isPresent()) remember(body, observation);
                return observation;
            }
        }
        return unsupported(body);
    }

    static Observation capture(Entity body, net.minecraft.world.level.BlockGetter geometry,
                                       net.minecraft.core.BlockPos support) {
        var result = contact(body, geometry, support);
        if (result.support().isPresent()) return result;
        // Minecraft's cached getOnPos is a hint, not a second coordinate authority.
        // A moved body may still name its previous support. Resolve its actual
        // centre column against collision, including fractional/protruding tops.
        int feet = (int) Math.floor(body.getBoundingBox().minY);
        for (int y = feet; y >= feet - 2; y--) {
            var candidate = new net.minecraft.core.BlockPos(body.getBlockX(), y, body.getBlockZ());
            if (candidate.equals(support)) continue;
            if (geometry instanceof net.minecraft.world.level.chunk.LevelChunk chunk
                    && !chunk.getPos().equals(new net.minecraft.world.level.ChunkPos(candidate))) continue;
            result = contact(body, geometry, candidate);
            if (result.support().isPresent()) return result;
        }
        return unsupported(body);
    }

    private static Observation contact(Entity body, net.minecraft.world.level.BlockGetter geometry,
                                       net.minecraft.core.BlockPos support) {
        if (geometry instanceof net.minecraft.world.level.chunk.LevelChunk chunk
                && !chunk.getPos().equals(new net.minecraft.world.level.ChunkPos(support))) return unsupported(body);
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

    private static void remember(Entity body, Observation observation) {
        DEPARTING_CONTACTS.put(body, new DepartingContact(body.getX(), body.getY(), body.getZ(), observation));
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
            remember(body, observation);
        } else DEPARTING_CONTACTS.remove(body);
    }

    /** Storage and final removal share the same witness, bound to the exact serialized pose.
     * A loaded column is always observed afresh: cached contact cannot hide changed terrain.
     * Motion after the terrain callback invalidates the witness, rather than rounding feet Y.
     */
    static Observation captureForDeparture(Entity body) {
        var support = body.getOnPos();
        var current = capture(body);
        if (current.support().isPresent()) return current;
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
        body.setOnGround(hasDryContact(level, body.getBoundingBox()));
    }

    /** Physical support belongs to the footprint, not necessarily its centre column.
     * Reads already loaded terrain only; side walls and blocks below a gap are not contact. */
    static boolean hasDryContact(ServerLevel level, net.minecraft.world.phys.AABB bounds) {
        int y = (int) Math.floor(bounds.minY - 0.000001D);
        boolean contact = false;
        for (int x = (int) Math.floor(bounds.minX + 0.000001D); x <= (int) Math.floor(bounds.maxX - 0.000001D); x++)
            for (int z = (int) Math.floor(bounds.minZ + 0.000001D); z <= (int) Math.floor(bounds.maxZ - 0.000001D); z++) {
                var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) return false;
                var support = new net.minecraft.core.BlockPos(x, y, z);
                if (!chunk.getFluidState(support).isEmpty() || !chunk.getFluidState(support.above()).isEmpty()) return false;
                for (var box : chunk.getBlockState(support).getCollisionShape(chunk, support).toAabbs())
                    if (bounds.maxX > x + box.minX && bounds.minX < x + box.maxX
                            && bounds.maxZ > z + box.minZ && bounds.minZ < z + box.maxZ
                            && Math.abs(bounds.minY - (y + box.maxY)) <= 0.01D) contact = true;
            }
        return contact;
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
