package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Read-only consumers of the retained street plan; no independent Manhattan compiler. */
public final class SettlementLocalCirculation {
    private static final ImmutableResultView<String, List<SurfaceAnchor>> PATHS =
            new ImmutableResultView<>(SimulationCapacity.MAX_SETTLEMENTS * 4);

    public static Set<BlockPosition> surfaceCells(Settlement settlement) {
        return settlement.layout().paving();
    }

    public static TraversalTopology topology(Settlement settlement) {
        return SettlementStreetTopology.of(settlement.layout());
    }

    static Set<BlockPosition> foundationCells(TerrainSurfacePlan terrain, Settlement settlement) {
        return Set.of();
    }

    public static List<SurfaceAnchor> infirmarySidewalk(Settlement settlement) {
        var hall =
                settlement.structures().stream()
                        .filter(s -> s.kind() == StructureKind.HALL)
                        .findFirst()
                        .orElseThrow();
        var infirmary =
                settlement.structures().stream()
                        .filter(s -> s.kind() == StructureKind.INFIRMARY)
                        .findFirst()
                        .orElseThrow();
        return path(
                settlement,
                SettlementAccessPort.forHall(hall).routeSurface(),
                SettlementInfirmaryTreatmentPort.forInfirmary(infirmary).exteriorApproachSurface());
    }

    public static List<SurfaceAnchor> path(
            Settlement settlement, SurfaceAnchor from, SurfaceAnchor to) {
        return PATHS.get(
                List.of(settlement.layout()),
                from + ">" + to,
                () -> compilePath(settlement, from, to));
    }

    private static List<SurfaceAnchor> compilePath(
            Settlement settlement, SurfaceAnchor from, SurfaceAnchor to) {
        var previous = new HashMap<SurfaceAnchor, SurfaceAnchor>();
        var queue = new ArrayDeque<SurfaceAnchor>();
        queue.add(from);
        previous.put(from, from);
        var columns = new HashMap<TerrainColumn, SurfaceAnchor>();
        settlement
                .layout()
                .paving()
                .forEach(p -> columns.put(new TerrainColumn(p.x(), p.z()), new SurfaceAnchor(p)));
        while (!queue.isEmpty() && !previous.containsKey(to)) {
            var p = queue.remove();
            for (var d : FacilityFacing.values()) {
                var q = columns.get(new TerrainColumn(p.x() + d.x(), p.z() + d.z()));
                if (q != null && Math.abs(q.y() - p.y()) <= 1 && previous.putIfAbsent(q, p) == null)
                    queue.add(q);
            }
        }
        if (!previous.containsKey(to))
            throw new IllegalArgumentException(
                    "disconnected public street endpoints: " + from + " -> " + to);
        var path = new ArrayList<SurfaceAnchor>();
        for (var p = to; ; p = previous.get(p)) {
            path.add(p);
            if (p.equals(from)) break;
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }

    private SettlementLocalCirculation() {}
}
