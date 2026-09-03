package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Compiles the one retained pedestrian corridor from an exact crafter to a workshop work station.
 *
 * <p>The free terrain approach ends at the workshop's declared exterior port; the final ingress,
 * input and work surfaces are appended from that same semantic port. Neither a COLD reducer nor
 * a HOT navigator may later choose a nearer wall, a different entrance or an alternate station.</p>
 */
public final class ProductionWorkTraversal {
    private static final int MAX_APPROACH_RADIUS = 96;
    private static final int MAX_EXPLORED_SURFACES = 8_192;

    private ProductionWorkTraversal() { }

    public static TraversalTopology compile(FrontierBootstrap bootstrap, SettlementStructure workshop,
                                            ActorLocation worker, SubjectId jobId) {
        Objects.requireNonNull(bootstrap, "production bootstrap"); Objects.requireNonNull(workshop, "production workshop");
        Objects.requireNonNull(worker, "production worker"); Objects.requireNonNull(jobId, "production job");
        SettlementWorkshopServicePort port = SettlementWorkshopServicePort.forWorkshop(workshop);
        SurfaceAnchor start = worker.supportingSurface(); SurfaceAnchor exterior = port.exteriorApproach();
        Set<BlockPosition> blocked = immutableBodyObstacles(bootstrap, port, start);
        List<SurfaceAnchor> corridor = new ArrayList<>(approach(bootstrap, start, exterior, blocked));
        List<SurfaceAnchor> ingress = port.topologyPort().ingressSurfaces();
        corridor.addAll(ingress.subList(1, ingress.size()));
        corridor.add(port.inputStation()); corridor.add(port.workStation());
        if (new LinkedHashSet<>(corridor).size() != corridor.size()) {
            throw new IllegalArgumentException("production-work corridor repeats a semantic surface");
        }
        return TraversalTopology.corridor(new TraversalTopologyId("topology:production-work-" + jobId.value().replace(':', '-')),
                revision(corridor), workshop.id(), TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN), corridor);
    }

    private static Set<BlockPosition> immutableBodyObstacles(FrontierBootstrap bootstrap, SettlementWorkshopServicePort port,
                                                               SurfaceAnchor start) {
        Set<BlockPosition> blocked = new HashSet<>();
        for (Settlement settlement : bootstrap.settlements()) {
            blocked.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(bootstrap.terrain(), settlement.structures()));
        }
        blocked.addAll(FrontierGrayboxPlan.intactOrganOccupancy(bootstrap.hive().organs()));
        blocked.remove(start.support());
        List<SurfaceAnchor> permitted = new ArrayList<>(port.topologyPort().ingressSurfaces());
        permitted.add(port.inputStation()); permitted.add(port.workStation());
        permitted.forEach(surface -> blocked.remove(surface.support()));
        return blocked;
    }

    private static List<SurfaceAnchor> approach(FrontierBootstrap bootstrap, SurfaceAnchor start, SurfaceAnchor target,
                                                Set<BlockPosition> blocked) {
        if (start.equals(target)) return List.of(start);
        record Candidate(SurfaceAnchor surface, int distance) { }
        PriorityQueue<Candidate> queue = new PriorityQueue<>(Comparator
                .comparingInt((Candidate value) -> Math.addExact(value.distance(), manhattan(value.surface(), target)))
                .thenComparingInt(Candidate::distance).thenComparingInt(value -> value.surface().x())
                .thenComparingInt(value -> value.surface().y()).thenComparingInt(value -> value.surface().z()));
        Map<SurfaceAnchor, SurfaceAnchor> predecessor = new HashMap<>(); Map<SurfaceAnchor, Integer> distance = new HashMap<>();
        queue.add(new Candidate(start, 0)); predecessor.put(start, null); distance.put(start, 0);
        int explored = 0;
        while (!queue.isEmpty()) {
            Candidate candidate = queue.remove(); SurfaceAnchor current = candidate.surface();
            if (candidate.distance() != distance.get(current)) continue;
            if (++explored > MAX_EXPLORED_SURFACES) throw new IllegalArgumentException("production-work approach exceeds bounded exploration");
            for (int[] delta : orderedNeighbours()) {
                int x = current.x() + delta[0], z = current.z() + delta[1];
                if (Math.abs(x - start.x()) > MAX_APPROACH_RADIUS || Math.abs(z - start.z()) > MAX_APPROACH_RADIUS) continue;
                SurfaceAnchor next = x == target.x() && z == target.z() ? target
                        : SurfaceAnchor.at(x, Math.addExact(bootstrap.terrain().supportYAt(x, z), 1), z);
                if (!bootstrap.bounds().contains(next.support()) || Math.abs(next.y() - current.y()) > 1 || blocked(next, blocked)) continue;
                int nextDistance = Math.addExact(candidate.distance(), 1); Integer known = distance.get(next);
                if (known != null && known <= nextDistance) continue;
                predecessor.put(next, current); distance.put(next, nextDistance);
                if (next.equals(target)) return reconstruct(predecessor, target);
                queue.add(new Candidate(next, nextDistance));
            }
        }
        throw new IllegalArgumentException("production-work approach has no bounded immutable route to workshop port");
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
