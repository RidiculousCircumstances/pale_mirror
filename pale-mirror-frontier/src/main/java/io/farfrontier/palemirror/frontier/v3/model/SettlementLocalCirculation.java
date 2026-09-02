package io.farfrontier.palemirror.frontier.v3.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Small immutable public pedestrian network compiled from semantic facility ports.
 *
 * <p>The current graybox has infirmary and depot service ports per settlement. Its sidewalks are deliberately
 * compiled here, rather than being inferred by a navigator from a hall centre or terrain query.
 * Later facilities add their own port-to-network connector to this same plan.</p>
 */
public final class SettlementLocalCirculation {
    private SettlementLocalCirculation() { }

    public static List<SurfaceAnchor> infirmarySidewalk(Settlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        SettlementStructure hall = settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("settlement has no Hall"));
        SettlementStructure infirmary = settlement.structures().stream().filter(value -> value.kind() == StructureKind.INFIRMARY)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("settlement has no infirmary"));
        SurfaceAnchor publicRoute = SettlementAccessPort.forHall(hall).routeSurface();
        SurfaceAnchor exterior = SettlementInfirmaryTreatmentPort.forInfirmary(infirmary).exteriorApproachSurface();
        return manhattanWithFinalGrade(publicRoute, exterior);
    }

    public static TraversalTopology topology(Settlement settlement) {
        List<List<SurfaceAnchor>> branches = circulationBranches(settlement);
        long revision = revision(branches.stream().flatMap(List::stream).toList());
        Map<TraversalNodeId, SurfaceAnchor> nodes = new LinkedHashMap<>();
        Map<String, TraversalTopology.Edge> edges = new LinkedHashMap<>();
        for (List<SurfaceAnchor> branch : branches) for (int index = 0; index < branch.size(); index++) {
            SurfaceAnchor surface = branch.get(index); TraversalNodeId node = node(surface);
            nodes.putIfAbsent(node, surface);
            if (index == 0) continue;
            TraversalNodeId prior = node(branch.get(index - 1));
            addBidirectional(edges, prior, node, branch.get(index - 1), surface, revision);
        }
        return new TraversalTopology(new TraversalTopologyId("topology:circulation-" + settlement.id().value().substring("settlement:".length())),
                revision, settlement.id(), nodes, List.copyOf(edges.values()));
    }

    public static Set<BlockPosition> surfaceCells(Settlement settlement) {
        LinkedHashSet<BlockPosition> cells = new LinkedHashSet<>();
        circulationBranches(settlement).forEach(branch -> branch.forEach(surface -> cells.add(surface.support())));
        return java.util.Collections.unmodifiableSet(cells);
    }

    private static List<List<SurfaceAnchor>> circulationBranches(Settlement settlement) {
        SettlementStructure depot = settlement.structures().stream().filter(value -> value.kind() == StructureKind.DEPOT)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("settlement has no depot"));
        SurfaceAnchor publicRoute = SettlementAccessPort.forHall(settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("settlement has no Hall"))).routeSurface();
        SurfaceAnchor depotExterior = SettlementDepotServicePort.forDepot(depot).exteriorApproach();
        return List.of(infirmarySidewalk(settlement), manhattanWithFinalGrade(publicRoute, depotExterior));
    }

    private static TraversalNodeId node(SurfaceAnchor surface) {
        return new TraversalNodeId("node:" + surface.x() + ":" + surface.y() + ":" + surface.z());
    }

    private static void addBidirectional(Map<String, TraversalTopology.Edge> edges, TraversalNodeId from, TraversalNodeId to,
                                         SurfaceAnchor fromSurface, SurfaceAnchor toSurface, long revision) {
        Set<TraversalCapability> capabilities = Set.of(TraversalCapability.PEDESTRIAN, TraversalCapability.GROUND_BIOFORM);
        addEdge(edges, from, to, fromSurface, toSurface, revision, capabilities);
        addEdge(edges, to, from, toSurface, fromSurface, revision, capabilities);
    }

    private static void addEdge(Map<String, TraversalTopology.Edge> edges, TraversalNodeId from, TraversalNodeId to,
                                SurfaceAnchor fromSurface, SurfaceAnchor toSurface, long revision,
                                Set<TraversalCapability> capabilities) {
        String key = from.value() + ">" + to.value();
        edges.putIfAbsent(key, new TraversalTopology.Edge(new TraversalEdgeId("edge:" + key), from, to, TraversalKind.PEDESTRIAN,
                capabilities, Math.abs(fromSurface.y() - toSurface.y()), 2, revision, TraversalAvailability.OPEN));
    }

    private static List<SurfaceAnchor> manhattanWithFinalGrade(SurfaceAnchor start, SurfaceAnchor target) {
        if (Math.abs(start.y() - target.y()) > 1) throw new IllegalArgumentException("local circulation grade exceeds one block");
        List<SurfaceAnchor> result = new ArrayList<>(); result.add(start);
        int x = start.x(), y = start.y(), z = start.z();
        // Reserve one horizontal step for the grade transition so no vertical-only edge enters
        // the canonical graph. Deterministic X-before-Z is a compiler policy, never navigation.
        int reserveX = target.x(), reserveZ = target.z();
        if (start.y() != target.y()) {
            if (x != target.x()) reserveX -= Integer.signum(target.x() - x);
            else if (z != target.z()) reserveZ -= Integer.signum(target.z() - z);
            else throw new IllegalArgumentException("local circulation cannot represent a vertical-only grade");
        }
        while (x != reserveX) { x += Integer.signum(reserveX - x); result.add(SurfaceAnchor.at(x, y, z)); }
        while (z != reserveZ) { z += Integer.signum(reserveZ - z); result.add(SurfaceAnchor.at(x, y, z)); }
        if (x != target.x() || y != target.y()) result.add(target);
        if (new LinkedHashSet<>(result).size() != result.size()) throw new IllegalArgumentException("local circulation repeats a surface");
        return List.copyOf(result);
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
