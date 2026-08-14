package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

/** Compiles the immutable low-capacity railway without owning chunk storage. */
final class FrontierRailGenesisCompiler {
    private FrontierRailGenesisCompiler() { }

    static void compile(List<VisualPoint> nodes, Sink sink) {
        for (int index = 0; index < nodes.size(); index++) {
            VisualPoint point = nodes.get(index);
            BlockPos rail = new BlockPos(point.x(), point.y(), point.z());
            Direction previous = direction(nodes, Math.max(0, index - 1), index == 0 ? 1 : index);
            Direction next = direction(nodes, index == nodes.size() - 1 ? index - 1 : index,
                    index == nodes.size() - 1 ? index : index + 1);
            int nextY = nodes.get(Math.min(nodes.size() - 1, index + 1)).y();
            int previousY = nodes.get(Math.max(0, index - 1)).y();
            RailShape shape = railShape(previous, next, point.y(), nextY, previousY);
            boolean powered = index > 0 && index % 12 == 0 && supportsPoweredRail(shape);
            BlockState state = powered ? Blocks.POWERED_RAIL.defaultBlockState()
                    .setValue(PoweredRailBlock.SHAPE, shape)
                    : Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape);
            sink.rail(rail, state, powered ? Blocks.REDSTONE_BLOCK.defaultBlockState()
                    : Blocks.STONE_BRICKS.defaultBlockState());
            if (index % 3 == 0) {
                boolean xAxis = next.getAxis() == Direction.Axis.X;
                for (int side : new int[]{-1, 1}) sink.block(rail.below().offset(
                        xAxis ? 0 : side, 0, xAxis ? side : 0), Blocks.STRIPPED_OAK_LOG.defaultBlockState());
            }
        }
    }

    private static Direction direction(List<VisualPoint> nodes, int from, int to) {
        VisualPoint a = nodes.get(from);
        VisualPoint b = nodes.get(to);
        if (b.x() > a.x()) return Direction.EAST;
        if (b.x() < a.x()) return Direction.WEST;
        return b.z() > a.z() ? Direction.SOUTH : Direction.NORTH;
    }

    private static RailShape railShape(Direction previous, Direction next, int y, int nextY, int previousY) {
        if (nextY > y) return ascending(next);
        if (previousY > y) return ascending(previous.getOpposite());
        return previous.getAxis() == next.getAxis() ? (next.getAxis() == Direction.Axis.X
                ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH) : corner(previous.getOpposite(), next);
    }

    private static RailShape ascending(Direction direction) {
        return switch (direction) {
            case EAST -> RailShape.ASCENDING_EAST;
            case WEST -> RailShape.ASCENDING_WEST;
            case SOUTH -> RailShape.ASCENDING_SOUTH;
            default -> RailShape.ASCENDING_NORTH;
        };
    }

    private static boolean supportsPoweredRail(RailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH, EAST_WEST, ASCENDING_EAST, ASCENDING_WEST, ASCENDING_NORTH,
                    ASCENDING_SOUTH -> true;
            default -> false;
        };
    }

    private static RailShape corner(Direction a, Direction b) {
        boolean north = a == Direction.NORTH || b == Direction.NORTH;
        boolean south = a == Direction.SOUTH || b == Direction.SOUTH;
        boolean east = a == Direction.EAST || b == Direction.EAST;
        if (north && east) return RailShape.NORTH_EAST;
        if (north) return RailShape.NORTH_WEST;
        return south && east ? RailShape.SOUTH_EAST : RailShape.SOUTH_WEST;
    }

    interface Sink {
        void rail(BlockPos rail, BlockState state, BlockState support);
        void block(BlockPos position, BlockState state);
    }
}
