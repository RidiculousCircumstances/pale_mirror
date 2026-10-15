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

/** The sole shape extraction adapter. Never acquires a chunk ticket or exports a Minecraft object. */
final class FrontierV3NavigationShapes extends net.minecraft.world.level.pathfinder.WalkNodeEvaluator {
    private static final net.minecraft.world.phys.shapes.CollisionContext STATIC_CONTEXT = new net.minecraft.world.phys.shapes.CollisionContext() {
        public boolean isDescending() { throw Unavailable.INSTANCE; }
        public boolean isAbove(net.minecraft.world.phys.shapes.VoxelShape shape, BlockPos pos, boolean fallback) { throw Unavailable.INSTANCE; }
        public boolean isHoldingItem(net.minecraft.world.item.Item item) { throw Unavailable.INSTANCE; }
        public boolean canStandOnFluid(FluidState current, FluidState above) { throw Unavailable.INSTANCE; }
    };
    private FrontierV3NavigationShapes() { }
    static Optional<NavigationCell> capture(ServerLevel level, BlockPosition position) {
        var origin = new BlockPos(position.x(), position.y(), position.z());
        // Retain only self-state shapes. External/contextual dependencies are
        // explicit unknown evidence, not a fabricated static collision shape.
        var reader = new BlockGetter() {
            private BlockState read(BlockPos pos) {
                // Retained cells contain exact self-state provenance, not a
                // dependency graph. Do not retain neighbour-dependent shapes
                // until their provider can supply those explicit dependencies.
                if (!pos.equals(origin)) throw Unavailable.INSTANCE;
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk == null) throw Unavailable.INSTANCE;
                return chunk.getBlockState(pos);
            }
            @Override public BlockState getBlockState(BlockPos pos) { return read(pos); }
            @Override public FluidState getFluidState(BlockPos pos) { return read(pos).getFluidState(); }
            @Override public BlockEntity getBlockEntity(BlockPos pos) { throw Unavailable.INSTANCE; }
            @Override public int getHeight() { return level.getHeight(); }
            @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
        };
        return capture(reader, origin);
    }
    /** A declared state may be resolved before chunk loading only if it needs no neighbouring/world evidence. */
    static Optional<NavigationCell> declared(BlockState state, BlockPosition position, int height, int minimumY) {
        var origin = new BlockPos(position.x(), position.y(), position.z());
        var reader = new BlockGetter() {
            @Override public BlockState getBlockState(BlockPos pos) { if (!pos.equals(origin)) throw Unavailable.INSTANCE; return state; }
            @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            @Override public BlockEntity getBlockEntity(BlockPos pos) { throw Unavailable.INSTANCE; }
            @Override public int getHeight() { return height; }
            @Override public int getMinBuildHeight() { return minimumY; }
        };
        return capture(reader, origin);
    }
    private static Optional<NavigationCell> capture(BlockGetter reader, BlockPos origin) {
        try {
            var state = reader.getBlockState(origin);
            // Dry-ground navigation excludes the fluid voxel for every supported
            // profile. This is a known hazard, not unknown neighbouring geometry.
            // Do not ask the fluid's entity-dependent stand-on-water collision
            // context: swimming/equipment capabilities are outside this provider.
            if (!state.getFluidState().isEmpty()) return Optional.of(new NavigationCell(
                    NavigationShape.FULL, false, Optional.of(FrontierV3BlockStates.describe(state))));
            // Minecraft explicitly marks non-cacheable/contextual shapes. An
            // empty CollisionContext is not evidence for every actor (snow,
            // scaffolding, equipment-dependent and stateful modded shapes).
            if (state.getBlock().hasDynamicShape()) return Optional.empty();
            var collision = state.getCollisionShape(reader, origin, STATIC_CONTEXT);
            if (collision.toAabbs().size() > NavigationShape.MAX_BOXES) return Optional.empty();
            var boxes = new ArrayList<NavigationShape.Box>();
            for (var box : collision.toAabbs()) {
                if (box.minX < -1 || box.minY < -1 || box.minZ < -1 || box.maxX > 2 || box.maxY > 2 || box.maxZ > 2)
                    return Optional.empty();
                boxes.add(new NavigationShape.Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
            }
            var type = getPathTypeFromState(reader, origin);
            boolean safe = state.getFluidState().isEmpty() && switch (type) {
                case DAMAGE_FIRE, DAMAGE_OTHER, DAMAGE_CAUTIOUS, DANGER_FIRE, DANGER_OTHER,
                        POWDER_SNOW, DANGER_POWDER_SNOW, LAVA, WATER, STICKY_HONEY -> false;
                default -> true;
            };
            return Optional.of(new NavigationCell(new NavigationShape(boxes), safe,
                    Optional.of(FrontierV3BlockStates.describe(state))));
        } catch (Unavailable missing) { return Optional.empty(); }
    }
    private static final class Unavailable extends RuntimeException {
        static final Unavailable INSTANCE = new Unavailable();
        private Unavailable() { super("unavailable bounded collision dependency", null, false, false); }
    }
}
