package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.*;

/** Test ingress on actual network-owned roads, independent of a settlement's street grammar. */
public final class DeclaredRouteFixtures {
    public static BlockPosition networkSurface(
            FrontierBootstrap bootstrap, RouteTopology topology, SubjectId owner) {
        return FrontierRouteNetwork.expandWaypoints(topology.settlementWaypoints(bootstrap, owner))
                .stream()
                .filter(p -> FrontierRouteNetwork.isSurfaceCell(bootstrap, topology, p))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("fixture requires a network-owned road"));
    }

    public static List<BlockPosition> raised(
            FrontierBootstrap bootstrap, RouteTopology topology, SubjectId owner) {
        var baseline = topology.settlementWaypoints(bootstrap, owner);
        for (int i = 1; i < baseline.size(); i++) {
            var a = baseline.get(i - 1);
            var b = baseline.get(i);
            int dx = Integer.signum(b.x() - a.x()), dz = Integer.signum(b.z() - a.z());
            if (a.y() != b.y() || Math.abs(b.x() - a.x()) + Math.abs(b.z() - a.z()) < 18) continue;
            var start = a.offset(dx * 6, 0, dz * 6);
            if (!FrontierRouteNetwork.isSurfaceCell(bootstrap, topology, start)) continue;
            var result = new ArrayList<>(baseline.subList(0, i));
            result.add(start);
            result.add(a.offset(dx * 8, 2, dz * 8));
            result.add(a.offset(dx * 10, 2, dz * 10));
            result.add(a.offset(dx * 12, 0, dz * 12));
            result.addAll(baseline.subList(i, baseline.size()));
            FrontierRouteNetwork.validateSettlementWaypoints(bootstrap, owner, result);
            return List.copyOf(result);
        }
        throw new IllegalStateException(
                "fixture requires a flat external road segment for a surveyed rise");
    }

    public static List<BlockPosition> bypass(
            FrontierBootstrap bootstrap, RouteTopology topology, SubjectId owner) {
        var baseline = topology.settlementWaypoints(bootstrap, owner);
        for (int i = 1; i < baseline.size(); i++) {
            var a = baseline.get(i - 1);
            var b = baseline.get(i);
            int dx = Integer.signum(b.x() - a.x()), dz = Integer.signum(b.z() - a.z());
            if (a.y() != b.y() || Math.abs(b.x() - a.x()) + Math.abs(b.z() - a.z()) < 18) continue;
            var start = a.offset(dx * 6, 0, dz * 6);
            var end = a.offset(dx * 14, 0, dz * 14);
            if (!FrontierRouteNetwork.isSurfaceCell(bootstrap, topology, start)
                    || !FrontierRouteNetwork.isSurfaceCell(bootstrap, topology, end)) continue;
            for (int side : new int[] {1, -1}) {
                var result = new ArrayList<>(baseline.subList(0, i));
                result.add(start);
                result.add(start.offset(-dz * 4 * side, 0, dx * 4 * side));
                result.add(end.offset(-dz * 4 * side, 0, dx * 4 * side));
                result.add(end);
                result.addAll(baseline.subList(i, baseline.size()));
                var expanded =
                        FrontierRouteNetwork.expandWaypoints(
                                List.of(
                                        start,
                                        start.offset(-dz * 4 * side, 0, dx * 4 * side),
                                        end.offset(-dz * 4 * side, 0, dx * 4 * side),
                                        end));
                if (expanded.stream()
                        .anyMatch(
                                p ->
                                        !bootstrap.bounds().contains(p)
                                                || SettlementLandIndex.intersectsPrivatePlots(
                                                        bootstrap, p.x(), p.z(), p.x(), p.z())))
                    continue;
                if (FrontierRouteNetwork.constructionCells(bootstrap, topology, owner, result)
                        .isEmpty()) continue;
                return List.copyOf(result);
            }
        }
        throw new IllegalStateException("fixture requires a clear bounded external bypass");
    }

    public record PatrolIngress(FrontierWorldState state, RouteUnitManifest unit) {}

    /**
     * Local scene/diagnostic ingress begins with the exact pair staged at the Hall, not a legacy
     * ring home.
     */
    public static PatrolIngress stagedPatrol(
            FrontierWorldState state,
            Settlement settlement,
            SubjectId task,
            SubjectId leader,
            SubjectId scout) {
        var hall =
                settlement.structures().stream()
                        .filter(b -> b.kind() == StructureKind.HALL)
                        .findFirst()
                        .orElseThrow();
        var port = SettlementAccessPort.forHall(hall);
        var staged =
                state.withActorBody(leader, port.routeSurface().standingBody())
                        .withActorBody(scout, port.assemblySurface().standingBody());
        return new PatrolIngress(staged, RouteUnitManifest.patrol(task, leader, List.of(scout)));
    }

    public static SurfaceAnchor openStreet(FrontierWorldState state, Settlement home) {
        var paving=home.layout().paving();
        var geometry=KnownPedestrianRouteKnowledge.forPublicJourney(state,home.id(),List.of());
        return paving.stream().filter(p -> java.util.stream.Stream.of(p.offset(2,0,0),p.offset(-2,0,0),p.offset(0,0,2),p.offset(0,0,-2)).allMatch(paving::contains))
                .filter(p -> !SettlementLandIndex.intersectsPrivatePlots(state.bootstrap(),p.x(),p.z(),p.x(),p.z()))
                .sorted(Comparator.comparingInt((BlockPosition p) -> Math.abs(p.x()-home.anchor().x())+Math.abs(p.z()-home.anchor().z()))
                        .thenComparingInt(BlockPosition::x).thenComparingInt(BlockPosition::z))
                .map(SurfaceAnchor::new).filter(p -> geometry.traversable(List.of(p)))
                .findFirst().orElseThrow(() -> new IllegalStateException("fixture requires an actual open public street"));
    }
    private DeclaredRouteFixtures() {}
}
