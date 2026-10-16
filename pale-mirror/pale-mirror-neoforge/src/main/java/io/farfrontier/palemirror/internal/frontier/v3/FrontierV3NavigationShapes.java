package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import java.util.*;

/** Sole native shape adapter. Bounded neighbour reads are retained; no chunk tickets or entity guesses. */
final class FrontierV3NavigationShapes extends net.minecraft.world.level.pathfinder.WalkNodeEvaluator {
    private static final net.minecraft.world.phys.shapes.CollisionContext STATIC_CONTEXT = new net.minecraft.world.phys.shapes.CollisionContext() {
        public boolean isDescending() { throw unavailable(NavigationObservation.Kind.ENTITY_CONTEXT_REQUIRED); }
        public boolean isAbove(net.minecraft.world.phys.shapes.VoxelShape shape, BlockPos pos, boolean fallback) { throw unavailable(NavigationObservation.Kind.ENTITY_CONTEXT_REQUIRED); }
        public boolean isHoldingItem(net.minecraft.world.item.Item item) { throw unavailable(NavigationObservation.Kind.ENTITY_CONTEXT_REQUIRED); }
        public boolean canStandOnFluid(FluidState current, FluidState above) { throw unavailable(NavigationObservation.Kind.ENTITY_CONTEXT_REQUIRED); }
    };
    private FrontierV3NavigationShapes() { }
    static NavigationObservation capture(ServerLevel level, BlockPosition position) {
        return capture(new Reader(position, level.getHeight(), level.getMinBuildHeight(), pos -> {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) throw unavailable(NavigationObservation.Kind.CHUNK_UNAVAILABLE);
            return chunk.getBlockState(pos);
        }));
    }
    /** Position-independent catalogue entries cannot retain neighbour or offset-dependent facts. */
    static NavigationObservation declared(BlockState state, BlockPosition position, int height, int minimumY) {
        if (state.hasOffsetFunction()) return NavigationObservation.unavailable(NavigationObservation.Kind.POSITION_REQUIRED);
        var observation = capture(new Reader(position, height, minimumY, pos -> {
            if (!pos.equals(new BlockPos(position.x(), position.y(), position.z())))
                throw unavailable(NavigationObservation.Kind.NEIGHBOUR_UNAVAILABLE);
            return state;
        }));
        return observation.cell().isPresent() ? observation : NavigationObservation.unavailable(observation.kind());
    }
    private static final class Reader implements BlockGetter {
        final BlockPos origin;
        final int height, minimumY;
        final java.util.function.Function<BlockPos, BlockState> blocks;
        final Map<BlockPosition, Optional<NavigationBlockState>> dependencies = new HashMap<>();
        Reader(BlockPosition position, int height, int minimumY, java.util.function.Function<BlockPos, BlockState> blocks) {
            origin = new BlockPos(position.x(), position.y(), position.z());
            this.height = height; this.minimumY = minimumY; this.blocks = blocks;
        }
        @Override public BlockState getBlockState(BlockPos pos) {
            if (!pos.equals(origin)) {
                if (Math.abs((long) pos.getX() - origin.getX()) > NavigationObservation.DEPENDENCY_RADIUS
                        || Math.abs((long) pos.getY() - origin.getY()) > NavigationObservation.DEPENDENCY_RADIUS
                        || Math.abs((long) pos.getZ() - origin.getZ()) > NavigationObservation.DEPENDENCY_RADIUS)
                    throw unavailable(NavigationObservation.Kind.DEPENDENCY_LIMIT);
                dependencies.put(new BlockPosition(pos.getX(), pos.getY(), pos.getZ()), Optional.empty());
            }
            var state = blocks.apply(pos);
            if (!pos.equals(origin)) dependencies.put(new BlockPosition(pos.getX(), pos.getY(), pos.getZ()),
                    Optional.of(FrontierV3BlockStates.describe(state)));
            return state;
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { throw unavailable(NavigationObservation.Kind.BLOCK_ENTITY_REQUIRED); }
        @Override public int getHeight() { return height; }
        @Override public int getMinBuildHeight() { return minimumY; }
    }
    private static NavigationObservation capture(Reader reader) {
        try {
            var origin = reader.origin; var state = reader.getBlockState(origin);
            if (!state.getFluidState().isEmpty()) return NavigationObservation.known(new NavigationCell(
                    NavigationShape.FULL, false, Optional.of(FrontierV3BlockStates.describe(state))));
            // Contextual blocks can branch on the concrete CollisionContext class
            // without calling any of its methods (e.g. powder snow). A synthetic
            // context therefore cannot establish a universal dynamic shape.
            if (state.getBlock().hasDynamicShape()) throw unavailable(NavigationObservation.Kind.DYNAMIC_SHAPE_UNSUPPORTED);
            var collision = state.getCollisionShape(reader, origin, STATIC_CONTEXT);
            if (collision.toAabbs().size() > NavigationShape.MAX_BOXES) throw unavailable(NavigationObservation.Kind.SHAPE_LIMIT);
            var boxes = new ArrayList<NavigationShape.Box>();
            for (var box : collision.toAabbs()) {
                if (box.minX < -1 || box.minY < -1 || box.minZ < -1 || box.maxX > 2 || box.maxY > 2 || box.maxZ > 2)
                    throw unavailable(NavigationObservation.Kind.SHAPE_LIMIT);
                boxes.add(new NavigationShape.Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
            }
            var type = getPathTypeFromState(reader, origin);
            boolean safe = switch (type) {
                case DAMAGE_FIRE, DAMAGE_OTHER, DAMAGE_CAUTIOUS, DANGER_FIRE, DANGER_OTHER,
                        POWDER_SNOW, DANGER_POWDER_SNOW, LAVA, WATER, STICKY_HONEY -> false;
                default -> true;
            };
            return NavigationObservation.known(new NavigationCell(new NavigationShape(boxes), safe,
                    Optional.of(FrontierV3BlockStates.describe(state))), reader.dependencies);
        } catch (Unavailable missing) {
            return NavigationObservation.unavailable(missing.kind, reader.dependencies);
        }
    }
    private static Unavailable unavailable(NavigationObservation.Kind kind) { return new Unavailable(kind); }
    private static final class Unavailable extends RuntimeException {
        final NavigationObservation.Kind kind;
        Unavailable(NavigationObservation.Kind kind) { super(kind.name(), null, false, false); this.kind = kind; }
    }
}
