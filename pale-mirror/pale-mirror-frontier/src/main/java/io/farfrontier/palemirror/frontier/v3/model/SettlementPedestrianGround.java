package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The finite, immutable pedestrian support datum owned by one settlement.
 *
 * <p>The resident apron/ingress and local circulation are physical ground, not merely
 * actor-placement hints.  Route compilers and the graybox projection must therefore obtain
 * a column's support from this provider before falling back to surveyed natural terrain.
 * A conflicting pair of owner surfaces in one column is invalid topology; it is never an
 * invitation for an actuator to guess a height at runtime.</p>
 */
public final class SettlementPedestrianGround {
    private static FrontierBootstrap cachedBootstrap;
    private static final Map<SubjectId, Map<TerrainColumn, SurfaceAnchor>> LOCAL = new LinkedHashMap<>();
    private SettlementPedestrianGround() { }

    public static synchronized Map<TerrainColumn, SurfaceAnchor> localSupports(FrontierBootstrap bootstrap, SubjectId settlementId) {
        Objects.requireNonNull(bootstrap, "settlement pedestrian ground bootstrap");
        Objects.requireNonNull(settlementId, "settlement pedestrian ground settlement");
        if (cachedBootstrap != bootstrap) {
            LOCAL.clear();
            cachedBootstrap = bootstrap;
        }
        var prior = LOCAL.get(settlementId);
        if (prior != null) return prior;
        var result = compile(bootstrap, settlementId);
        LOCAL.put(settlementId, result); // Only validated settlements in the one current bootstrap.
        return result;
    }

    private static Map<TerrainColumn, SurfaceAnchor> compile(FrontierBootstrap bootstrap, SubjectId settlementId) {
        Settlement settlement = bootstrap.settlements().stream().filter(value -> value.id().equals(settlementId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("settlement pedestrian ground has no settlement: " + settlementId.value()));
        LinkedHashMap<TerrainColumn, SurfaceAnchor> surfaces = new LinkedHashMap<>();
        SettlementLocalCirculation.surfaceCells(settlement).forEach(position -> add(surfaces, new SurfaceAnchor(position)));
        // Service stations are materialized support floors, not the natural terrain beneath
        // them. A route through the lower terrain datum would put the worker's feet inside
        // the depot/workshop sill even though its endpoint is the correct elevated station.
        settlement.structures().forEach(structure -> {
            FrontierGrayboxPlan.publicAccessSurfaces(structure).forEach(surface -> add(surfaces, surface));
            // A declared threshold/connector rests on the authored floor, not on
            // natural terrain beneath it. Knowledge of support does not grant
            // permission to enter: the passage view still owns headroom/access.
            FrontierTraversalPlan.facilityPort(structure).ifPresent(port ->
                    port.ingressSurfaces().forEach(surface -> add(surfaces, surface)));
        });
        SettlementResidentIngressPlan.compile(bootstrap.bounds(), bootstrap.terrain(), settlement,
                bootstrap.ruleset().facilityCapacity().intactHousingBeds()).ownedSurfaces().forEach(surface -> add(surfaces, surface));
        return Collections.unmodifiableMap(new LinkedHashMap<>(surfaces));
    }

    public static SurfaceAnchor surveyedSupport(FrontierBootstrap bootstrap, Map<TerrainColumn, SurfaceAnchor> localSupports, int x, int z) {
        Objects.requireNonNull(bootstrap, "settlement pedestrian surveyed bootstrap");
        Objects.requireNonNull(localSupports, "settlement pedestrian local supports");
        return localSupports.getOrDefault(new TerrainColumn(x, z), SurfaceAnchor.at(x, bootstrap.terrain().supportYAt(x, z), z));
    }

    private static void add(Map<TerrainColumn, SurfaceAnchor> surfaces, SurfaceAnchor surface) {
        TerrainColumn column = new TerrainColumn(surface.x(), surface.z());
        SurfaceAnchor prior = surfaces.putIfAbsent(column, surface);
        if (prior != null && !prior.equals(surface)) {
            throw new IllegalArgumentException("settlement pedestrian ground has multiple support datums at " + column);
        }
    }
}
