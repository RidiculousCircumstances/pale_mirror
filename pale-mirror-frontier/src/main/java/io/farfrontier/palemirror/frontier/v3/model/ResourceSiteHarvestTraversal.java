package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Compiles the one retained pedestrian corridor for a named field worker.
 *
 * <p>The corridor starts at the worker's canonical support surface, reaches the first field
 * workstation through immutable surveyed terrain and structure occupancy, then visits every
 * crop workstation in the field's stable serpentine order.  It is deliberately a pure compiler:
 * it does not inspect loaded blocks, ask Minecraft to find a path, or choose an alternate route
 * after an obstruction.  A HOT scene may only move toward the next retained surface.</p>
 */
public final class ResourceSiteHarvestTraversal {
    private static final int MAX_APPROACH_RADIUS = 96;
    private static final int MAX_EXPLORED_SURFACES = 8_192;

    private ResourceSiteHarvestTraversal() { }

    public static TraversalTopology compile(FrontierBootstrap bootstrap, ResourceSite site, ActorLocation worker, SubjectId jobId) {
        Objects.requireNonNull(bootstrap, "harvest bootstrap"); Objects.requireNonNull(site, "harvest site");
        Objects.requireNonNull(worker, "harvest worker"); Objects.requireNonNull(jobId, "harvest job");
        SurfaceAnchor start = worker.supportingSurface();
        List<SurfaceAnchor> workstations = site.cropSlots().stream().map(slot -> new SurfaceAnchor(slot.offset(0, -1, 0))).toList();
        SurfaceAnchor first = workstations.getFirst();
        Set<BlockPosition> blocked = immutableBodyObstacles(bootstrap, site, first.support());
        List<SurfaceAnchor> approach = approach(bootstrap, start, first, blocked);
        List<SurfaceAnchor> corridor = new ArrayList<>(approach);
        for (int index = 1; index < workstations.size(); index++) corridor.add(workstations.get(index));
        if (new LinkedHashSet<>(corridor).size() != corridor.size()) {
            throw new IllegalArgumentException("field-work corridor repeats a semantic surface");
        }
        return TraversalTopology.corridor(new TraversalTopologyId("topology:field-work-" + jobId.value().replace(':', '-')),
                revision(corridor), site.id(), TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN), corridor);
    }

    private static Set<BlockPosition> immutableBodyObstacles(FrontierBootstrap bootstrap, ResourceSite site, BlockPosition firstWorkstation) {
        Set<BlockPosition> blocked = new HashSet<>();
        for (Settlement settlement : bootstrap.settlements()) {
            blocked.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(bootstrap.terrain(), settlement.structures()));
        }
        blocked.addAll(FrontierGrayboxPlan.intactOrganOccupancy(bootstrap.hive().organs()));
        // Crop supports are workstations, never a shortcut through a field.  The exact first
        // station is the declared endpoint and is therefore intentionally left available.
        for (BlockPosition crop : site.cropSlots()) {
            BlockPosition support = crop.offset(0, -1, 0);
            if (!support.equals(firstWorkstation)) blocked.add(support);
        }
        return blocked;
    }

    private static List<SurfaceAnchor> approach(FrontierBootstrap bootstrap, SurfaceAnchor start, SurfaceAnchor target,
                                                Set<BlockPosition> blocked) {
        if (start.equals(target)) return List.of(start);
        record Candidate(SurfaceAnchor surface, int distance) { }
        PriorityQueue<Candidate> queue = new PriorityQueue<>(java.util.Comparator
                .comparingInt((Candidate value) -> Math.addExact(value.distance(), manhattan(value.surface(), target)))
                .thenComparingInt(Candidate::distance).thenComparingInt(value -> value.surface().x())
                .thenComparingInt(value -> value.surface().y()).thenComparingInt(value -> value.surface().z()));
        Map<SurfaceAnchor, SurfaceAnchor> predecessor = new HashMap<>();
        Map<SurfaceAnchor, Integer> distance = new HashMap<>();
        queue.add(new Candidate(start, 0)); predecessor.put(start, null); distance.put(start, 0);
        int explored = 0;
        while (!queue.isEmpty()) {
            Candidate currentCandidate = queue.remove(); SurfaceAnchor current = currentCandidate.surface();
            if (currentCandidate.distance() != distance.get(current)) continue;
            if (++explored > MAX_EXPLORED_SURFACES) throw new IllegalArgumentException("field-work approach exceeds bounded exploration");
            for (int[] delta : orderedNeighbours()) {
                int x = current.x() + delta[0], z = current.z() + delta[1];
                if (Math.abs(x - start.x()) > MAX_APPROACH_RADIUS || Math.abs(z - start.z()) > MAX_APPROACH_RADIUS) continue;
                SurfaceAnchor next = (x == target.x() && z == target.z()) ? target
                        : SurfaceAnchor.at(x, Math.addExact(bootstrap.terrain().supportYAt(x, z), 1), z);
                if (!bootstrap.bounds().contains(next.support()) || Math.abs(next.y() - current.y()) > 1 || blocked(next, blocked)) continue;
                int nextDistance = Math.addExact(currentCandidate.distance(), 1);
                Integer known = distance.get(next); if (known != null && known <= nextDistance) continue;
                predecessor.put(next, current); distance.put(next, nextDistance);
                if (next.equals(target)) return reconstruct(predecessor, target);
                queue.add(new Candidate(next, nextDistance));
            }
        }
        throw new IllegalArgumentException("field-work approach has no bounded immutable route from " + start.support() + " to " + target.support());
    }

    private static int manhattan(SurfaceAnchor first, SurfaceAnchor second) {
        return Math.addExact(Math.abs(first.x() - second.x()), Math.abs(first.z() - second.z()));
    }

    private static boolean blocked(SurfaceAnchor surface, Set<BlockPosition> occupied) {
        return occupied.contains(surface.support()) || occupied.contains(surface.support().offset(0, 1, 0))
                || occupied.contains(surface.support().offset(0, 2, 0));
    }

    private static List<SurfaceAnchor> reconstruct(Map<SurfaceAnchor, SurfaceAnchor> predecessor, SurfaceAnchor target) {
        List<SurfaceAnchor> reverse = new ArrayList<>();
        for (SurfaceAnchor current = target; current != null; current = predecessor.get(current)) reverse.add(current);
        java.util.Collections.reverse(reverse); return List.copyOf(reverse);
    }

    private static List<int[]> orderedNeighbours() {
        // Compiler order only: no live navigator reads or expands this preference.
        return List.of(new int[] { 1, 0 }, new int[] { 0, -1 }, new int[] { -1, 0 }, new int[] { 0, 1 });
    }

    private static long revision(List<SurfaceAnchor> surfaces) {
        long hash = 0xcbf29ce484222325L;
        for (SurfaceAnchor surface : surfaces) {
            hash = (hash ^ Integer.toUnsignedLong(surface.x())) * 0x100000001b3L;
            hash = (hash ^ Integer.toUnsignedLong(surface.y())) * 0x100000001b3L;
            hash = (hash ^ Integer.toUnsignedLong(surface.z())) * 0x100000001b3L;
        }
        return hash & Long.MAX_VALUE;
    }
}
