package io.farfrontier.palemirror.internal.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.RailShape;

/** Pure persisted-graph connectivity for a vanilla rail contract. */
final class VanillaRailTopology {
    private static final List<Direction> DIRECTIONS = List.of(Direction.NORTH, Direction.EAST,
            Direction.SOUTH, Direction.WEST);

    private VanillaRailTopology() { }

    static Optional<List<BlockPos>> path(Map<Long, String> encodedShapes, BlockPos start, BlockPos target) {
        if (!encodedShapes.containsKey(start.asLong()) || !encodedShapes.containsKey(target.asLong())) {
            return Optional.empty();
        }
        Map<Long, Long> previous = new HashMap<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        previous.put(start.asLong(), start.asLong());
        pending.add(start);
        while (!pending.isEmpty()) {
            BlockPos current = pending.removeFirst();
            if (current.equals(target)) return Optional.of(reconstruct(previous, start, target));
            RailShape currentShape = decode(encodedShapes.get(current.asLong()));
            for (Direction direction : DIRECTIONS) {
                if (!allows(currentShape, direction)) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos candidate = current.relative(direction).above(dy);
                    String encoded = encodedShapes.get(candidate.asLong());
                    if (encoded == null || previous.containsKey(candidate.asLong())) continue;
                    RailShape candidateShape = decode(encoded);
                    if (!allows(candidateShape, direction.getOpposite())) continue;
                    if (!heightCompatible(currentShape, direction, candidateShape, dy)) continue;
                    previous.put(candidate.asLong(), current.asLong());
                    pending.addLast(candidate);
                }
            }
        }
        return Optional.empty();
    }

    static Map<Long, String> ordered(Map<Long, String> nodes) {
        Map<Long, String> result = new LinkedHashMap<>();
        nodes.entrySet().stream().sorted(Comparator.comparingLong(Map.Entry::getKey))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private static List<BlockPos> reconstruct(Map<Long, Long> previous, BlockPos start, BlockPos target) {
        ArrayList<BlockPos> reverse = new ArrayList<>();
        long cursor = target.asLong();
        while (true) {
            reverse.add(BlockPos.of(cursor));
            if (cursor == start.asLong()) break;
            cursor = previous.get(cursor);
        }
        java.util.Collections.reverse(reverse);
        return List.copyOf(reverse);
    }

    private static boolean heightCompatible(RailShape from, Direction direction, RailShape to, int dy) {
        if (dy == 0) return true;
        if (dy > 0) return ascends(from, direction);
        return ascends(to, direction.getOpposite());
    }

    private static boolean ascends(RailShape shape, Direction direction) {
        return switch (shape) {
            case ASCENDING_NORTH -> direction == Direction.NORTH;
            case ASCENDING_SOUTH -> direction == Direction.SOUTH;
            case ASCENDING_EAST -> direction == Direction.EAST;
            case ASCENDING_WEST -> direction == Direction.WEST;
            default -> false;
        };
    }

    private static boolean allows(RailShape shape, Direction direction) {
        return switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> direction.getAxis() == Direction.Axis.Z;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> direction.getAxis() == Direction.Axis.X;
            case SOUTH_EAST -> direction == Direction.SOUTH || direction == Direction.EAST;
            case SOUTH_WEST -> direction == Direction.SOUTH || direction == Direction.WEST;
            case NORTH_WEST -> direction == Direction.NORTH || direction == Direction.WEST;
            case NORTH_EAST -> direction == Direction.NORTH || direction == Direction.EAST;
        };
    }

    private static RailShape decode(String value) {
        try {
            return RailShape.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unknown persisted rail shape " + value, exception);
        }
    }
}
