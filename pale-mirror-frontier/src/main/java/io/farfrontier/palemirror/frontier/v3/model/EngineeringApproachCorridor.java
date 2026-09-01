package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded deterministic approach compiler for a construction crew.
 *
 * <p>Engineering does not need an unbounded general pathfinder: it approaches one declared
 * graybox work site on a finite, flat world. It therefore tries a small stable catalogue of
 * rectilinear public lanes, retains the first wholly clear corridor, and blocks admission if
 * none is available. The retained result is still the exact COLD path consumed one cell at a
 * time; this compiler never teleports a worker or asks Minecraft to invent topology.</p>
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
        if (start.y() != destination.y()) throw new IllegalArgumentException("engineering approach changes its canonical anchor plane");
        Set<BlockPosition> bodyGeometry = FrontierGrayboxPlan.currentBodyGeometry(state);
        Set<BlockPosition> occupiedFloors = occupiedFloors(state, actorId);
        if (!traversable(state.bootstrap().bounds(), bodyGeometry, occupiedFloors, start)
                || !traversable(state.bootstrap().bounds(), bodyGeometry, occupiedFloors, destination)) {
            throw new IllegalArgumentException("engineering approach has no clear actor or work-site endpoint");
        }
        List<List<BlockPosition>> clear = new ArrayList<>();
        for (List<BlockPosition> candidate : pathCandidates(state.bootstrap().bounds(), start, destination)) {
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

    private static List<List<BlockPosition>> pathCandidates(WorldBounds bounds, BlockPosition start, BlockPosition destination) {
        List<List<BlockPosition>> candidates = new ArrayList<>();
        add(candidates, route(start, new BlockPosition(start.x(), start.y(), destination.z()), destination));
        add(candidates, route(start, new BlockPosition(destination.x(), start.y(), start.z()), destination));
        for (int lane : lanes(bounds.minX() + 2, bounds.maxXExclusive() - 3, start.x(), destination.x())) {
            add(candidates, route(start, new BlockPosition(lane, start.y(), start.z()), new BlockPosition(lane, start.y(), destination.z()), destination));
        }
        for (int lane : lanes(bounds.minZ() + 2, bounds.maxZExclusive() - 3, start.z(), destination.z())) {
            add(candidates, route(start, new BlockPosition(start.x(), start.y(), lane), new BlockPosition(destination.x(), start.y(), lane), destination));
        }
        return List.copyOf(candidates);
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
        if (!candidates.contains(route)) candidates.add(route);
    }

    private static List<BlockPosition> route(BlockPosition... points) {
        List<BlockPosition> result = new ArrayList<>();
        for (int index = 1; index < points.length; index++) addSegment(result, points[index - 1], points[index], index == 1);
        return List.copyOf(result);
    }

    private static void addSegment(List<BlockPosition> cells, BlockPosition from, BlockPosition to, boolean includeStart) {
        if (from.y() != to.y() || from.x() != to.x() && from.z() != to.z()) throw new IllegalArgumentException("engineering approach segment is not axis aligned");
        int stepX = Integer.compare(to.x(), from.x()), stepZ = Integer.compare(to.z(), from.z());
        for (int x = from.x(), z = from.z();; x += stepX, z += stepZ) {
            if (includeStart || x != from.x() || z != from.z()) cells.add(new BlockPosition(x, from.y(), z));
            if (x == to.x() && z == to.z()) return;
        }
    }

    private static boolean traversable(WorldBounds bounds, Set<BlockPosition> bodyGeometry, Set<BlockPosition> occupiedFloors, BlockPosition floor) {
        return bounds.contains(floor) && !occupiedFloors.contains(floor)
                && !bodyGeometry.contains(floor.offset(0, 1, 0)) && !bodyGeometry.contains(floor.offset(0, 2, 0));
    }
}
