package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.MineFoundationPlan;
import io.farfrontier.palemirror.api.VisualPortKind;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Obstacle-aware, stable and walkable surface circulation for an authored MineSite. */
final class MineAccessGenesisCompiler {
    static final int ROAD_RADIUS = 2;
    private static final int ACCESS_CLEARANCE = ROAD_RADIUS + 2;
    private static final int SEARCH_MARGIN = 14;

    private MineAccessGenesisCompiler() { }

    static void compile(AuthoredMineSitePlan mine, FrontierPalette palette, Sink sink) {
        compile(routes(mine), palette, sink);
    }

    static void compile(List<AccessRoute> routes, FrontierPalette palette, Sink sink) {
        for (AccessRoute route : routes) compileRoute(route.points(), palette, sink);
    }

    static Set<Long> occupied(List<AccessRoute> routes) {
        Set<Long> result = new HashSet<>();
        for (AccessRoute route : routes) {
            List<VisualPoint> points = route.points();
            for (int index = 0; index < points.size(); index++) {
                VisualPoint point = points.get(index);
                VisualPoint previous = points.get(Math.max(0, index - 1));
                VisualPoint next = points.get(Math.min(points.size() - 1, index + 1));
                int dx = Integer.signum(next.x() - previous.x());
                int dz = Integer.signum(next.z() - previous.z());
                boolean turn = index > 0 && index + 1 < points.size()
                        && (previous.x() != point.x()) != (next.x() != point.x());
                for (Cell cell : turn ? square(point) : crossSection(point, dx, dz)) {
                    result.add(net.minecraft.world.level.ChunkPos.asLong(cell.x(), cell.z()));
                }
            }
        }
        return Set.copyOf(result);
    }

    static List<AccessRoute> routes(AuthoredMineSitePlan mine) {
        java.util.Set<String> activeIds = MineSurfaceLayout.materializedFoundationIds(mine);
        List<MineFoundationPlan> foundations = mine.foundations().stream()
                .filter(value -> activeIds.contains(value.id())).toList();
        if (foundations.isEmpty()) throw new IllegalStateException("MineSite has no active surface foundations");
        int hubY = foundations.stream().mapToInt(MineFoundationPlan::targetY).sorted()
                .skip(foundations.size() / 2).findFirst().orElseThrow();
        VisualPoint desiredHub = MineSurfaceLayout.local(
                mine.portal(), 0, -32, 0, mine.inwardQuarterTurns());
        Bounds search = Bounds.around(foundations, SEARCH_MARGIN);
        Set<Cell> blocked = blocked(foundations, ROAD_RADIUS);
        Cell hub = nearestFree(new Cell(desiredHub.x(), desiredHub.z()), blocked, search);
        SearchTree tree = search(hub, blocked, search);
        List<AccessRoute> result = new ArrayList<>();
        for (MineFoundationPlan foundation : foundations) {
            boolean minePortal = foundation.id().equals("portal");
            List<Approach> candidates = minePortal
                    ? List.of(minePortalApproach(mine)) : approaches(mine, foundation);
            Approach approach = candidates.stream()
                    .filter(value -> tree.distance().containsKey(value.outer()))
                    .min(Comparator.comparingInt((Approach value) -> value.preferred() ? 0 : 1)
                            .thenComparingInt(value -> tree.distance().get(value.outer())))
                    .orElseThrow(() -> new IllegalStateException(
                            "MineSite access could not reach " + foundation.id()));
            List<Cell> horizontal = tree.path(approach.outer());
            for (int step = 1; step < approach.clearance(); step++) {
                horizontal.add(new Cell(approach.outer().x() + approach.stepX() * step,
                        approach.outer().z() + approach.stepZ() * step));
            }
            List<VisualPoint> graded = new ArrayList<>(horizontal.size());
            int segments = Math.max(1, horizontal.size() - 1);
            int targetY = approach.targetY();
            for (int index = 0; index < horizontal.size(); index++) {
                Cell cell = horizontal.get(index);
                int y = hubY + (targetY - hubY) * index / segments;
                graded.add(new VisualPoint(cell.x(), y, cell.z()));
            }
            result.add(new AccessRoute(foundation.id(), List.copyOf(graded),
                    approach.stepX(), approach.stepZ(), minePortal));
        }
        return List.copyOf(result);
    }

    /** The main road terminates at the real adit, not at the hoist-house side door. */
    private static Approach minePortalApproach(AuthoredMineSitePlan mine) {
        int inwardX = directionX(mine.inwardQuarterTurns());
        int inwardZ = directionZ(mine.inwardQuarterTurns());
        return new Approach(new Cell(mine.portal().x() - inwardX * ACCESS_CLEARANCE,
                mine.portal().z() - inwardZ * ACCESS_CLEARANCE),
                inwardX, inwardZ, ACCESS_CLEARANCE, mine.portal().y() - 1, true);
    }

    private static void compileRoute(List<VisualPoint> points, FrontierPalette palette, Sink sink) {
        for (int index = 0; index < points.size(); index++) {
            VisualPoint point = points.get(index);
            VisualPoint previous = points.get(Math.max(0, index - 1));
            VisualPoint next = points.get(Math.min(points.size() - 1, index + 1));
            int dx = Integer.signum(next.x() - previous.x());
            int dz = Integer.signum(next.z() - previous.z());
            boolean turn = index > 0 && index + 1 < points.size()
                    && (previous.x() != point.x()) != (next.x() != point.x());
            List<Cell> cells = turn ? square(point) : crossSection(point, dx, dz);
            boolean lower = previous.y() > point.y() || next.y() > point.y();
            for (Cell cell : cells) {
                BlockState surface = Math.floorMod(cell.x() * 3 + cell.z() + index, 7) == 0
                        ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                        : Blocks.COBBLESTONE.defaultBlockState();
                sink.terrain(cell.x(), cell.z(), point.y(), surface, Blocks.STONE_BRICKS.defaultBlockState());
                if (lower) sink.block(new BlockPos(cell.x(), point.y() + 1, cell.z()), palette.pavingSlab());
                sink.cleanup(cell.x(), cell.z(), point.y());
            }
        }
    }

    private static List<Cell> crossSection(VisualPoint point, int dx, int dz) {
        List<Cell> result = new ArrayList<>(ROAD_RADIUS * 2 + 1);
        for (int offset = -ROAD_RADIUS; offset <= ROAD_RADIUS; offset++) {
            result.add(new Cell(point.x() - dz * offset, point.z() + dx * offset));
        }
        return result;
    }

    private static List<Cell> square(VisualPoint point) {
        List<Cell> result = new ArrayList<>();
        for (int dx = -ROAD_RADIUS; dx <= ROAD_RADIUS; dx++) {
            for (int dz = -ROAD_RADIUS; dz <= ROAD_RADIUS; dz++) {
                result.add(new Cell(point.x() + dx, point.z() + dz));
            }
        }
        return result;
    }

    private static List<Approach> approaches(AuthoredMineSitePlan mine, MineFoundationPlan foundation) {
        VisualBounds bounds = foundation.footprint();
        int centerX = (bounds.min().x() + bounds.max().x()) / 2;
        int centerZ = (bounds.min().z() + bounds.max().z()) / 2;
        List<Approach> result = new ArrayList<>();
        mine.surfaceBuildings().stream().flatMap(value -> value.modules().stream())
                .filter(value -> value.foundationId().equals(foundation.id()))
                .flatMap(value -> value.ports().stream())
                .filter(value -> value.kind() == VisualPortKind.PUBLIC_ENTRANCE)
                .findFirst().ifPresent(port -> {
                    int outwardX = directionX(port.outwardQuarterTurns());
                    int outwardZ = directionZ(port.outwardQuarterTurns());
                    int clearance = entranceClearance(bounds, port.position(), port.outwardQuarterTurns());
                    result.add(new Approach(new Cell(
                            port.position().x() + outwardX * clearance,
                            port.position().z() + outwardZ * clearance),
                            -outwardX, -outwardZ, clearance, port.position().y() - 1, true));
                });
        addDistinct(result, new Approach(new Cell(bounds.min().x() - ACCESS_CLEARANCE, centerZ),
                1, 0, ACCESS_CLEARANCE, foundation.targetY(), false));
        addDistinct(result, new Approach(new Cell(bounds.max().x() + ACCESS_CLEARANCE, centerZ),
                -1, 0, ACCESS_CLEARANCE, foundation.targetY(), false));
        addDistinct(result, new Approach(new Cell(centerX, bounds.min().z() - ACCESS_CLEARANCE),
                0, 1, ACCESS_CLEARANCE, foundation.targetY(), false));
        addDistinct(result, new Approach(new Cell(centerX, bounds.max().z() + ACCESS_CLEARANCE),
                0, -1, ACCESS_CLEARANCE, foundation.targetY(), false));
        return List.copyOf(result);
    }

    /** Reaches beyond the expanded obstacle while retaining the real authored door as the threshold. */
    private static int entranceClearance(VisualBounds bounds, VisualPoint entrance, int outward) {
        int distance = switch (Math.floorMod(outward, 4)) {
            case 0 -> bounds.max().x() + ROAD_RADIUS + 1 - entrance.x();
            case 1 -> bounds.max().z() + ROAD_RADIUS + 1 - entrance.z();
            case 2 -> entrance.x() - (bounds.min().x() - ROAD_RADIUS - 1);
            default -> entrance.z() - (bounds.min().z() - ROAD_RADIUS - 1);
        };
        return Math.max(ACCESS_CLEARANCE, distance);
    }

    private static void addDistinct(List<Approach> approaches, Approach candidate) {
        if (approaches.stream().noneMatch(value -> value.outer().equals(candidate.outer()))) {
            approaches.add(candidate);
        }
    }

    private static int directionX(int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> 1;
            case 2 -> -1;
            default -> 0;
        };
    }

    private static int directionZ(int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> 1;
            case 3 -> -1;
            default -> 0;
        };
    }

    private static Set<Cell> blocked(List<MineFoundationPlan> foundations, int expansion) {
        Set<Cell> result = new HashSet<>();
        for (MineFoundationPlan foundation : foundations) {
            VisualBounds bounds = foundation.footprint();
            for (int x = bounds.min().x() - expansion; x <= bounds.max().x() + expansion; x++) {
                for (int z = bounds.min().z() - expansion; z <= bounds.max().z() + expansion; z++) {
                    result.add(new Cell(x, z));
                }
            }
        }
        return result;
    }

    private static Cell nearestFree(Cell desired, Set<Cell> blocked, Bounds bounds) {
        for (int radius = 0; radius <= SEARCH_MARGIN; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                Cell candidate = new Cell(desired.x() + dx, desired.z() + dz);
                if (bounds.contains(candidate) && !blocked.contains(candidate)) return candidate;
            }
        }
        throw new IllegalStateException("MineSite has no free circulation hub");
    }

    private static SearchTree search(Cell start, Set<Cell> blocked, Bounds bounds) {
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Map<Cell, Cell> previous = new LinkedHashMap<>();
        Map<Cell, Integer> distance = new HashMap<>();
        queue.add(start); distance.put(start, 0);
        int[][] directions = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        while (!queue.isEmpty()) {
            Cell current = queue.removeFirst();
            for (int[] direction : directions) {
                Cell next = new Cell(current.x() + direction[0], current.z() + direction[1]);
                if (!bounds.contains(next) || blocked.contains(next) || distance.containsKey(next)) continue;
                previous.put(next, current);
                distance.put(next, distance.get(current) + 1);
                queue.addLast(next);
            }
        }
        return new SearchTree(start, previous, distance);
    }

    record AccessRoute(String foundationId, List<VisualPoint> points, int entryStepX, int entryStepZ,
                       boolean minePortal) { }
    private record Approach(Cell outer, int stepX, int stepZ, int clearance, int targetY,
                            boolean preferred) { }
    private record Cell(int x, int z) { }

    private record SearchTree(Cell start, Map<Cell, Cell> previous, Map<Cell, Integer> distance) {
        List<Cell> path(Cell destination) {
            ArrayList<Cell> reverse = new ArrayList<>();
            for (Cell current = destination; current != null; current = previous.get(current)) {
                reverse.add(current);
                if (current.equals(start)) break;
            }
            java.util.Collections.reverse(reverse);
            return reverse;
        }
    }

    private record Bounds(int minX, int maxX, int minZ, int maxZ) {
        static Bounds around(List<MineFoundationPlan> foundations, int margin) {
            return new Bounds(
                    foundations.stream().mapToInt(value -> value.footprint().min().x()).min().orElseThrow() - margin,
                    foundations.stream().mapToInt(value -> value.footprint().max().x()).max().orElseThrow() + margin,
                    foundations.stream().mapToInt(value -> value.footprint().min().z()).min().orElseThrow() - margin,
                    foundations.stream().mapToInt(value -> value.footprint().max().z()).max().orElseThrow() + margin);
        }

        boolean contains(Cell cell) {
            return cell.x() >= minX && cell.x() <= maxX && cell.z() >= minZ && cell.z() <= maxZ;
        }
    }

    interface Sink {
        void terrain(int x, int z, int targetY, BlockState surface, BlockState foundation);
        void cleanup(int x, int z, int baseY);
        void block(BlockPos position, BlockState state);
    }
}
