package io.farfrontier.palemirror.frontier.v3.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Pure bounded compiler for one retained pedestrian approach through immutable surveyed terrain.
 *
 * <p>Callers own their semantic station, occupancy set and surveyed surface convention. This
 * helper only compiles an ordered approach; it never observes loaded Minecraft blocks, chooses
 * a later detour, or accepts an unbounded search.</p>
 */
public final class BoundedPedestrianApproach {
    public static final int MAX_RADIUS = 96;
    public static final int MAX_EXPLORED_SURFACES = 8_192;

    @FunctionalInterface
    public interface SurveyedSurface {
        SurfaceAnchor at(int x, int z);
    }

    private BoundedPedestrianApproach() { }

    public static List<SurfaceAnchor> compile(FrontierBootstrap bootstrap, SurfaceAnchor start,
                                               SurfaceAnchor target, Set<BlockPosition> occupied,
                                               SurveyedSurface surveyedSurface, String owner) {
        Objects.requireNonNull(bootstrap, "pedestrian approach bootstrap");
        Objects.requireNonNull(start, "pedestrian approach start");
        Objects.requireNonNull(target, "pedestrian approach target");
        occupied = Set.copyOf(Objects.requireNonNull(occupied, "pedestrian approach occupied cells"));
        surveyedSurface = Objects.requireNonNull(surveyedSurface, "pedestrian surveyed surface");
        owner = Objects.requireNonNull(owner, "pedestrian approach owner");
        if (start.equals(target)) return List.of(start);
        record Candidate(SurfaceAnchor surface, int distance) { }
        PriorityQueue<Candidate> queue = new PriorityQueue<>(Comparator
                .comparingInt((Candidate value) -> Math.addExact(value.distance(), manhattan(value.surface(), target)))
                .thenComparingInt(Candidate::distance).thenComparingInt(value -> value.surface().x())
                .thenComparingInt(value -> value.surface().y()).thenComparingInt(value -> value.surface().z()));
        Map<SurfaceAnchor, SurfaceAnchor> predecessor = new HashMap<>();
        Map<SurfaceAnchor, Integer> distance = new HashMap<>();
        queue.add(new Candidate(start, 0)); predecessor.put(start, null); distance.put(start, 0);
        int explored = 0;
        while (!queue.isEmpty()) {
            Candidate candidate = queue.remove(); SurfaceAnchor current = candidate.surface();
            if (candidate.distance() != distance.get(current)) continue;
            if (++explored > MAX_EXPLORED_SURFACES) throw new IllegalArgumentException(owner + " approach exceeds bounded exploration");
            for (int[] delta : orderedNeighbours()) {
                int x = current.x() + delta[0], z = current.z() + delta[1];
                if (Math.abs(x - start.x()) > MAX_RADIUS || Math.abs(z - start.z()) > MAX_RADIUS) continue;
                SurfaceAnchor next = x == target.x() && z == target.z() ? target : surveyedSurface.at(x, z);
                if (!bootstrap.bounds().contains(next.support()) || Math.abs(next.y() - current.y()) > 1 || blocked(next, occupied)) continue;
                int nextDistance = Math.addExact(candidate.distance(), 1); Integer known = distance.get(next);
                if (known != null && known <= nextDistance) continue;
                predecessor.put(next, current); distance.put(next, nextDistance);
                if (next.equals(target)) return reconstruct(predecessor, target);
                queue.add(new Candidate(next, nextDistance));
            }
        }
        throw new IllegalArgumentException(owner + " approach has no bounded immutable route from " + start.support() + " to " + target.support());
    }

    private static boolean blocked(SurfaceAnchor surface, Set<BlockPosition> occupied) {
        return occupied.contains(surface.support()) || occupied.contains(surface.support().offset(0, 1, 0))
                || occupied.contains(surface.support().offset(0, 2, 0));
    }

    private static int manhattan(SurfaceAnchor first, SurfaceAnchor second) {
        return Math.addExact(Math.abs(first.x() - second.x()), Math.abs(first.z() - second.z()));
    }

    private static List<SurfaceAnchor> reconstruct(Map<SurfaceAnchor, SurfaceAnchor> predecessor, SurfaceAnchor target) {
        List<SurfaceAnchor> reverse = new ArrayList<>();
        for (SurfaceAnchor current = target; current != null; current = predecessor.get(current)) reverse.add(current);
        java.util.Collections.reverse(reverse); return List.copyOf(reverse);
    }

    private static List<int[]> orderedNeighbours() {
        return List.of(new int[] { 1, 0 }, new int[] { 0, -1 }, new int[] { -1, 0 }, new int[] { 0, 1 });
    }
}
