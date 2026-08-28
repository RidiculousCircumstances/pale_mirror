package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Persisted, bounded route choices. The bootstrap graph is the baseline; this record contains
 * only accepted replacement paths, never a scan or adoption of player-built blocks.
 */
public record RouteTopology(Map<SubjectId, List<BlockPosition>> replacementSupplyRoutes) {
    private static final int MAX_REPLACEMENTS = 12;
    public RouteTopology {
        Objects.requireNonNull(replacementSupplyRoutes, "replacement supply routes");
        if (replacementSupplyRoutes.size() > MAX_REPLACEMENTS) throw new IllegalArgumentException("route replacement limit exceeded");
        Map<SubjectId, List<BlockPosition>> copy = new LinkedHashMap<>();
        replacementSupplyRoutes.forEach((settlement, path) -> {
            List<BlockPosition> immutablePath = List.copyOf(Objects.requireNonNull(path, "route path"));
            if (immutablePath.size() < 3 || immutablePath.size() > 127) throw new IllegalArgumentException("replacement route path size is out of bounds");
            if (copy.put(Objects.requireNonNull(settlement, "route settlement"), immutablePath) != null) {
                throw new IllegalArgumentException("duplicate replacement route settlement");
            }
        });
        replacementSupplyRoutes = Map.copyOf(copy);
    }

    public static RouteTopology initial() { return new RouteTopology(Map.of()); }

    public List<BlockPosition> supplyWaypoints(FrontierBootstrap bootstrap, SubjectId settlementId) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(settlementId, "settlement id");
        return replacementSupplyRoutes.getOrDefault(settlementId, FrontierRouteNetwork.supplyWaypoints(bootstrap, settlementId));
    }

    /** A caller must already have constructed every physical replacement cell before acceptance. */
    public RouteTopology replaceSupplyRoute(FrontierBootstrap bootstrap, SubjectId settlementId, List<BlockPosition> route) {
        FrontierRouteNetwork.validateSupplyWaypoints(bootstrap, settlementId, route);
        Map<SubjectId, List<BlockPosition>> next = new LinkedHashMap<>(replacementSupplyRoutes); next.put(settlementId, List.copyOf(route));
        return new RouteTopology(next);
    }
}
