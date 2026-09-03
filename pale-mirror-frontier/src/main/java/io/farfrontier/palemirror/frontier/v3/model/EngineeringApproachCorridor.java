package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded deterministic approach compiler for a construction crew.
 *
 * <p>Engineering does not need an unbounded general pathfinder: it approaches one declared
 * graybox work site through a small stable catalogue of rectilinear public lanes.  Every
 * horizontal cell is resolved against the immutable terrain provider plus an intact declared
 * route deck at that column (with the exact start and work-site support retained as endpoints),
 * and a candidate rejects a grade above one block.
 * The retained result is still the exact COLD path consumed one cell at a time; this compiler
 * never teleports a worker or asks Minecraft to invent terrain.</p>
 */
final class EngineeringApproachCorridor {
    private static final int[] LOCAL_LANE_OFFSETS = {-16, 16, -32, 32, -64, 64, -96, 96};

    private EngineeringApproachCorridor() { }

    static List<BlockPosition> compile(FrontierWorldState state, SubjectId actorId, BlockPosition destination) {
        List<List<BlockPosition>> candidates = candidates(state, actorId, destination);
        if (!candidates.isEmpty()) return candidates.getFirst();
        throw new IllegalArgumentException("engineering approach has no clear bounded public lane");
    }

    /** All bounded clear candidates in stable priority order for one joint crew compiler. */
    static List<List<BlockPosition>> candidates(FrontierWorldState state, SubjectId actorId, BlockPosition destination) {
        Objects.requireNonNull(state, "engineering approach state"); Objects.requireNonNull(actorId, "engineering approach actor");
        Objects.requireNonNull(destination, "engineering approach destination");
        BlockPosition start = Objects.requireNonNull(state.actorLocations().get(actorId), "engineering approach actor location").supportingSurface().support();
        Set<BlockPosition> bodyGeometry = FrontierGrayboxPlan.currentBodyGeometry(state);
        Set<BlockPosition> occupiedFloors = occupiedFloors(state, actorId);
        if (!traversable(state.bootstrap().bounds(), bodyGeometry, occupiedFloors, start)
                || !traversable(state.bootstrap().bounds(), bodyGeometry, occupiedFloors, destination)) {
            throw new IllegalArgumentException("engineering approach has no clear actor or work-site endpoint");
        }
        List<List<BlockPosition>> clear = new ArrayList<>();
        for (List<BlockPosition> candidate : pathCandidates(state, start, destination)) {
            if (candidate.size() <= OperationTravel.MAX_CELLS && candidate.stream()
                    .allMatch(cell -> traversable(state.bootstrap().bounds(), bodyGeometry, occupiedFloors, cell))) clear.add(candidate);
        }
        return List.copyOf(clear);
    }

    private static Set<BlockPosition> occupiedFloors(FrontierWorldState state, SubjectId actorId) {
        Set<BlockPosition> occupied = new LinkedHashSet<>();
        state.ambientLeases().entrySet().stream().filter(entry -> !entry.getKey().equals(actorId))
                .filter(entry -> entry.getValue().status() != AmbientLeaseStatus.CLOSED).map(Map.Entry::getKey)
                .map(state.actorLocations()::get).filter(Objects::nonNull)
                .filter(location -> location.condition().status() == ActorLifeStatus.ALIVE).map(ActorLocation::supportingSurface).map(SurfaceAnchor::support).forEach(occupied::add);
        return occupied;
    }

    private static List<List<BlockPosition>> pathCandidates(FrontierWorldState state, BlockPosition start, BlockPosition destination) {
        WorldBounds bounds = state.bootstrap().bounds();
        SurfaceIndex surfaces = SurfaceIndex.compile(state);
        List<List<BlockPosition>> candidates = new ArrayList<>();
        add(candidates, route(state, surfaces, start, terrainSurface(state, surfaces, start.x(), destination.z()), destination));
        add(candidates, route(state, surfaces, start, terrainSurface(state, surfaces, destination.x(), start.z()), destination));
        for (int lane : lanes(bounds.minX() + 2, bounds.maxXExclusive() - 3, start.x(), destination.x())) {
            add(candidates, route(state, surfaces, start, terrainSurface(state, surfaces, lane, start.z()), terrainSurface(state, surfaces, lane, destination.z()), destination));
        }
        for (int lane : lanes(bounds.minZ() + 2, bounds.maxZExclusive() - 3, start.z(), destination.z())) {
            add(candidates, route(state, surfaces, start, terrainSurface(state, surfaces, start.x(), lane), terrainSurface(state, surfaces, destination.x(), lane), destination));
        }
        return List.copyOf(candidates);
    }

    private static BlockPosition terrainSurface(FrontierWorldState state, SurfaceIndex surfaces, int x, int z) {
        BlockPosition terrain = new BlockPosition(x, state.bootstrap().terrain().supportYAt(x, z), z);
        return surfaces.at(x, z).filter(surface -> !state.physicalDeltas().containsKey(surface)).orElse(terrain);
    }

    private static List<Integer> lanes(int minimum, int maximum, int start, int destination) {
        LinkedHashSet<Integer> lanes = new LinkedHashSet<>(); lanes.add(minimum); lanes.add(maximum);
        for (int offset : LOCAL_LANE_OFFSETS) {
            addIfInBounds(lanes, start + offset, minimum, maximum); addIfInBounds(lanes, destination + offset, minimum, maximum);
        }
        return List.copyOf(lanes);
    }

    private static void addIfInBounds(Set<Integer> lanes, int lane, int minimum, int maximum) {
        if (lane >= minimum && lane <= maximum) lanes.add(lane);
    }

    private static void add(List<List<BlockPosition>> candidates, List<BlockPosition> route) {
        if (!route.isEmpty() && !candidates.contains(route)) candidates.add(route);
    }

    private static List<BlockPosition> route(FrontierWorldState state, SurfaceIndex surfaces, BlockPosition... points) {
        List<BlockPosition> result = new ArrayList<>();
        for (int index = 1; index < points.length; index++) {
            if (!addSegment(state, surfaces, result, points[index - 1], points[index], index == 1)) return List.of();
        }
        return List.copyOf(result);
    }

    private static boolean addSegment(FrontierWorldState state, SurfaceIndex surfaces, List<BlockPosition> cells, BlockPosition from, BlockPosition to, boolean includeStart) {
        if (from.x() != to.x() && from.z() != to.z()) throw new IllegalArgumentException("engineering approach segment is not axis aligned");
        int stepX = Integer.compare(to.x(), from.x()), stepZ = Integer.compare(to.z(), from.z());
        for (int x = from.x(), z = from.z();; x += stepX, z += stepZ) {
            boolean append = includeStart || x != from.x() || z != from.z();
            // Reject an overlong candidate before resolving another topology column. The
            // operation limit is a compiler budget, not a post-hoc validation after a path
            // finder has walked arbitrarily far through a finite world.
            if (append && cells.size() >= OperationTravel.MAX_CELLS) return false;
            BlockPosition cell = x == from.x() && z == from.z() ? from : x == to.x() && z == to.z() ? to : terrainSurface(state, surfaces, x, z);
            BlockPosition previous = cells.isEmpty() ? null : cells.getLast();
            if (previous != null && Math.abs(previous.y() - cell.y()) > 1) return false;
            if (append) cells.add(cell);
            if (x == to.x() && z == to.z()) return true;
        }
    }

    /**
     * One approach compilation resolves the immutable route projection once, then does bounded
     * O(1) column lookups. Re-scanning every route segment for every candidate cell made an
     * ordinary repair admission proportional to candidate length times the whole world graph.
     */
    private record SurfaceIndex(Map<Long, BlockPosition> columns) {
        static SurfaceIndex compile(FrontierWorldState state) {
            Map<Long, BlockPosition> columns = new HashMap<>();
            for (BlockPosition surface : FrontierRouteNetwork.surfaceCells(state.bootstrap(), state.routeTopology())) {
                columns.merge(key(surface.x(), surface.z()), surface,
                        (first, second) -> first.y() >= second.y() ? first : second);
            }
            return new SurfaceIndex(Map.copyOf(columns));
        }

        java.util.Optional<BlockPosition> at(int x, int z) {
            return java.util.Optional.ofNullable(columns.get(key(x, z)));
        }

        private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffff_ffffL); }
    }

    private static boolean traversable(WorldBounds bounds, Set<BlockPosition> bodyGeometry, Set<BlockPosition> occupiedFloors, BlockPosition floor) {
        return bounds.contains(floor) && !occupiedFloors.contains(floor)
                && !bodyGeometry.contains(floor.offset(0, 1, 0)) && !bodyGeometry.contains(floor.offset(0, 2, 0));
    }
}
