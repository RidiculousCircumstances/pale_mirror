package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;

/** Composes authored solid surfaces before natural terrain; no task owns a private road datum. */
public final class KnownPedestrianGround {
    private record Roads(RouteTopology topology, Map<TerrainColumn, SurfaceAnchor> surfaces) { }
    // Rebuildable knowledge cache: one current bootstrap identity/road revision, never world state.
    private static FrontierBootstrap cachedBootstrap;
    private static Roads cachedRoads;
    private static final Map<SubjectId, Map<TerrainColumn, SurfaceAnchor>> LOCAL = new LinkedHashMap<>();
    private KnownPedestrianGround() { }

    public static BoundedPedestrianApproach.SurveyedSurface forSettlement(FrontierWorldState state,
                                                                       SubjectId settlementId) {
        Map<TerrainColumn, SurfaceAnchor> roads = roads(state.bootstrap(), state.routeTopology());
        Map<TerrainColumn, SurfaceAnchor> local = local(state.bootstrap(), settlementId);
        var terrain = state.bootstrap().terrain();
        return (x, z) -> {
            TerrainColumn column = new TerrainColumn(x, z);
            SurfaceAnchor road = roads.get(column), settlement = local.get(column);
            // Both solid authored floors are physically projected. Only their upper exposed
            // collision surface is standable; selecting the lower puts feet inside the upper.
            if (road != null && settlement != null) return road.y() >= settlement.y() ? road : settlement;
            if (road != null) return road;
            return settlement != null ? settlement : SurfaceAnchor.at(x,
                    terrain.supportYAt(x, z), z);
        };
    }

    private static synchronized Map<TerrainColumn, SurfaceAnchor> roads(FrontierBootstrap bootstrap,
                                                                       RouteTopology topology) {
        useBootstrap(bootstrap);
        Roads cached = cachedRoads;
        if (cached != null && cached.topology() == topology) return cached.surfaces();
        Map<TerrainColumn, SurfaceAnchor> surfaces = new LinkedHashMap<>();
        for (BlockPosition position : FrontierRouteNetwork.footprint(bootstrap, topology).surfaceCells()) {
            TerrainColumn column = new TerrainColumn(position.x(), position.z());
            SurfaceAnchor candidate = new SurfaceAnchor(position);
            surfaces.merge(column, candidate, (left, right) -> left.y() >= right.y() ? left : right);
        }
        Map<TerrainColumn, SurfaceAnchor> immutable = Map.copyOf(surfaces);
        cachedRoads = new Roads(topology, immutable);
        return immutable;
    }

    private static synchronized Map<TerrainColumn, SurfaceAnchor> local(FrontierBootstrap bootstrap, SubjectId settlementId) {
        useBootstrap(bootstrap);
        return LOCAL.computeIfAbsent(settlementId, id -> SettlementPedestrianGround.localSupports(bootstrap, id));
    }

    private static void useBootstrap(FrontierBootstrap bootstrap) {
        if (cachedBootstrap == bootstrap) return;
        cachedBootstrap = bootstrap;
        cachedRoads = null;
        LOCAL.clear();
    }
}
