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
        /** All independently known contacts. The scalar view is only a presentation preference. */
        default List<SurfaceAnchor> surfacesAt(int x, int z) {
            var surface = at(x, z);
            return surface == null ? List.of() : List.of(surface);
        }
        /** An exact removed/invalid support is different from an unobserved level. */
        default boolean excluded(SurfaceAnchor surface) { return false; }
        default boolean clearanceKnown(SurfaceAnchor surface) { return true; }
        default boolean blockKnown(BlockPosition position) { return true; }
        default java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCell> collisionAt(BlockPosition position) { return java.util.Optional.empty(); }
        default boolean detailedCollisionAt(int x, int z) { return false; }
    }

    /** An ordinary bounded-search miss, not malformed geometry or an invalid call. */
    public static final class ApproachUnavailable extends IllegalArgumentException {
        public ApproachUnavailable(String detail) { super(detail); }
    }

    private BoundedPedestrianApproach() { }

    /** Explicit contact restrictions never masquerade as solid block volumes. */
    public static SurveyedSurface excluding(SurveyedSurface source, java.util.Set<BlockPosition> contacts) {
        var excluded = io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet.copyOf(contacts);
        if (excluded.isEmpty()) return source;
        return new SurveyedSurface() {
            @Override public SurfaceAnchor at(int x, int z) { return source.at(x, z); }
            @Override public List<SurfaceAnchor> surfacesAt(int x, int z) { return source.surfacesAt(x, z); }
            @Override public boolean clearanceKnown(SurfaceAnchor surface) { return source.clearanceKnown(surface); }
            @Override public boolean blockKnown(BlockPosition position) { return source.blockKnown(position); }
            @Override public java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCell> collisionAt(BlockPosition position) { return source.collisionAt(position); }
            @Override public boolean detailedCollisionAt(int x, int z) { return source.detailedCollisionAt(x, z); }
            @Override public boolean excluded(SurfaceAnchor surface) {
                return excluded.contains(surface.support()) || source.excluded(surface);
            }
        };
    }

    public static List<SurfaceAnchor> compile(FrontierBootstrap bootstrap, SurfaceAnchor start,
                                               SurfaceAnchor target, Set<BlockPosition> occupied,
                                               SurveyedSurface surveyedSurface, String owner) {
        return compile(bootstrap, start, target, occupied, surveyedSurface, owner, MAX_RADIUS, false);
    }

    /** Same geometry/search owner, explicitly authorized for a bounded cross-frontier journey. */
    public static List<SurfaceAnchor> compileJourney(FrontierBootstrap bootstrap, SurfaceAnchor start,
            SurfaceAnchor target, Set<BlockPosition> occupied, SurveyedSurface surveyedSurface, String owner) {
        return compile(bootstrap, start, target, occupied, surveyedSurface, owner, 1_024, true);
    }

    private static List<SurfaceAnchor> compile(FrontierBootstrap bootstrap, SurfaceAnchor start,
            SurfaceAnchor target, Set<BlockPosition> occupied, SurveyedSurface surveyedSurface, String owner,
            int radius, boolean journey) {
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
                .thenComparingInt(value -> journey ? -value.distance() : value.distance()).thenComparingInt(value -> value.surface().x())
                .thenComparingInt(value -> value.surface().y()).thenComparingInt(value -> value.surface().z()));
        Map<SurfaceAnchor, SurfaceAnchor> predecessor = new HashMap<>();
        Map<SurfaceAnchor, Integer> distance = new HashMap<>();
        queue.add(new Candidate(start, 0)); predecessor.put(start, null); distance.put(start, 0);
        int explored = 0;
        while (!queue.isEmpty()) {
            Candidate candidate = queue.remove(); SurfaceAnchor current = candidate.surface();
            if (candidate.distance() != distance.get(current)) continue;
            if (++explored > MAX_EXPLORED_SURFACES) throw new ApproachUnavailable(owner + " approach exceeds bounded exploration");
            for (int[] delta : orderedNeighbours()) {
                int x = current.x() + delta[0], z = current.z() + delta[1];
                if (Math.abs(x - start.x()) > radius || Math.abs(z - start.z()) > radius) continue;
                SurfaceAnchor next = x == target.x() && z == target.z() ? target : surveyedSurface.at(x, z);
                if (!bootstrap.bounds().contains(next.support()) || Math.abs(next.y() - current.y()) > 1 || blocked(next, occupied)) continue;
                int nextDistance = Math.addExact(candidate.distance(), 1); Integer known = distance.get(next);
                if (known != null && known <= nextDistance) continue;
                predecessor.put(next, current); distance.put(next, nextDistance);
                if (next.equals(target)) return reconstruct(predecessor, target);
                queue.add(new Candidate(next, nextDistance));
            }
        }
        throw new ApproachUnavailable(owner + " approach has no bounded immutable route from " + start.support() + " to " + target.support());
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
