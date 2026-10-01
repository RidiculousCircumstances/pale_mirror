package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Composes authored solid surfaces before natural terrain; no task owns a private road datum. */
public final class KnownPedestrianGround {
    private record Roads(RouteTopology topology, Map<TerrainColumn, SurfaceAnchor> surfaces) { }
    // Rebuildable knowledge cache: one current road revision per weak bootstrap, never world state.
    private static final Map<FrontierBootstrap, Roads> ROADS = new WeakHashMap<>();
    private KnownPedestrianGround() { }

    public static BoundedPedestrianApproach.SurveyedSurface forSettlement(FrontierWorldState state,
                                                                       SubjectId settlementId) {
        Map<TerrainColumn, SurfaceAnchor> roads = roads(state.bootstrap(), state.routeTopology());
        Map<TerrainColumn, SurfaceAnchor> local = SettlementPedestrianGround.localSupports(
                state.bootstrap(), settlementId);
        return (x, z) -> {
            TerrainColumn column = new TerrainColumn(x, z);
            SurfaceAnchor road = roads.get(column), settlement = local.get(column);
            // Both solid authored floors are physically projected. Only their upper exposed
            // collision surface is standable; selecting the lower puts feet inside the upper.
            if (road != null && settlement != null) return road.y() >= settlement.y() ? road : settlement;
            if (road != null) return road;
            return settlement != null ? settlement : SurfaceAnchor.at(x,
                    state.bootstrap().terrain().supportYAt(x, z), z);
        };
    }

    private static synchronized Map<TerrainColumn, SurfaceAnchor> roads(FrontierBootstrap bootstrap,
                                                                       RouteTopology topology) {
        Roads cached = ROADS.get(bootstrap);
        if (cached != null && cached.topology() == topology) return cached.surfaces();
        Map<TerrainColumn, SurfaceAnchor> surfaces = new LinkedHashMap<>();
        for (BlockPosition position : FrontierRouteNetwork.footprint(bootstrap, topology).surfaceCells()) {
            TerrainColumn column = new TerrainColumn(position.x(), position.z());
            SurfaceAnchor candidate = new SurfaceAnchor(position);
            surfaces.merge(column, candidate, (left, right) -> left.y() >= right.y() ? left : right);
        }
        Map<TerrainColumn, SurfaceAnchor> immutable = Map.copyOf(surfaces);
        ROADS.put(bootstrap, new Roads(topology, immutable));
        return immutable;
    }
}
