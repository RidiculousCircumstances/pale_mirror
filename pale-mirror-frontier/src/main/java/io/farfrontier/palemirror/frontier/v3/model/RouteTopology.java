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
public record RouteTopology(Map<SubjectId, List<BlockPosition>> replacementSupplyRoutes,
                            Map<SubjectId, Map<TraversalEdgeId, TraversalAvailability>> supplyAvailability) {
    public static final int MAX_REPLACEMENTS = 12;
    public static final int MIN_WAYPOINTS = 3;
    public static final int MAX_WAYPOINTS = 127;
    public RouteTopology(Map<SubjectId, List<BlockPosition>> replacementSupplyRoutes) {
        this(replacementSupplyRoutes, Map.of());
    }

    public RouteTopology {
        Objects.requireNonNull(replacementSupplyRoutes, "replacement supply routes");
        if (replacementSupplyRoutes.size() > MAX_REPLACEMENTS) throw new IllegalArgumentException("route replacement limit exceeded");
        Map<SubjectId, List<BlockPosition>> copy = new LinkedHashMap<>();
        replacementSupplyRoutes.forEach((settlement, path) -> {
            List<BlockPosition> immutablePath = List.copyOf(Objects.requireNonNull(path, "route path"));
            if (immutablePath.size() < MIN_WAYPOINTS || immutablePath.size() > MAX_WAYPOINTS) throw new IllegalArgumentException("replacement route path size is out of bounds");
            if (copy.put(Objects.requireNonNull(settlement, "route settlement"), immutablePath) != null) {
                throw new IllegalArgumentException("duplicate replacement route settlement");
            }
        });
        replacementSupplyRoutes = Map.copyOf(copy);
        Objects.requireNonNull(supplyAvailability, "supply traversal availability");
        Map<SubjectId, Map<TraversalEdgeId, TraversalAvailability>> availabilityCopy = new LinkedHashMap<>();
        supplyAvailability.forEach((settlement, edges) -> {
            SubjectId settlementId = Objects.requireNonNull(settlement, "supply availability settlement");
            Map<TraversalEdgeId, TraversalAvailability> edgeCopy = new LinkedHashMap<>();
            Objects.requireNonNull(edges, "supply availability edges").forEach((edge, availability) -> {
                if (edgeCopy.put(Objects.requireNonNull(edge, "supply availability edge"), Objects.requireNonNull(availability, "supply edge availability")) != null) {
                    throw new IllegalArgumentException("duplicate supply availability edge");
                }
            });
            if (edgeCopy.size() > TraversalTopology.MAX_EDGES || availabilityCopy.put(settlementId, Map.copyOf(edgeCopy)) != null) {
                throw new IllegalArgumentException("supply traversal availability is invalid");
            }
        });
        supplyAvailability = Map.copyOf(availabilityCopy);
    }

    public static RouteTopology initial() { return new RouteTopology(Map.of()); }

    public List<BlockPosition> supplyWaypoints(FrontierBootstrap bootstrap, SubjectId settlementId) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(settlementId, "settlement id");
        return replacementSupplyRoutes.getOrDefault(settlementId, FrontierRouteNetwork.supplyWaypoints(bootstrap, settlementId));
    }

    /**
     * The sole compiled ground topology for this settlement's declared supply route.
     *
     * <p>Persisted replacement waypoints remain the authority; this deterministic expansion
     * supplies stable nodes, edges, capabilities and a content revision without asking a
     * Minecraft navigator to discover a path. Rail is intentionally absent from this graph.</p>
     */
    public TraversalTopology supplyTraversalTopology(FrontierBootstrap bootstrap, SubjectId settlementId) {
        List<BlockPosition> expanded = FrontierRouteNetwork.expandWaypoints(supplyWaypoints(bootstrap, settlementId));
        TraversalTopology topology = TraversalTopology.corridor(new TraversalTopologyId("topology:route:" + settlementId.value()), contentRevision(expanded),
                FrontierRouteNetwork.OWNER, TraversalKind.PEDESTRIAN,
                java.util.Set.of(TraversalCapability.PEDESTRIAN, TraversalCapability.GROUND_BIOFORM),
                expanded.stream().map(SurfaceAnchor::new).toList());
        Map<TraversalEdgeId, TraversalAvailability> observed = supplyAvailability.getOrDefault(settlementId, Map.of());
        return topology.withExactAvailability(observed);
    }

    /** Returns the availability-adjusted retained segment in its actual direction of travel. */
    public TraversalTopology supplyTraversalSegment(FrontierBootstrap bootstrap, SubjectId settlementId,
                                                     BlockPosition from, BlockPosition to, TraversalTopologyId id) {
        TraversalTopology complete = supplyTraversalTopology(bootstrap, settlementId);
        List<SurfaceAnchor> corridor = complete.linearCorridorSurfaces();
        int fromIndex = corridor.indexOf(new SurfaceAnchor(from)), toIndex = corridor.indexOf(new SurfaceAnchor(to));
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) throw new IllegalArgumentException("supply segment is not on the retained topology");
        return complete.linearSegment(id, fromIndex, toIndex);
    }

    /** A known owned route-surface loss is physical evidence against every matching canonical edge. */
    public RouteTopology blockAffectedSupplyEdges(FrontierBootstrap bootstrap, BlockPosition physicalSurface) {
        Objects.requireNonNull(bootstrap, "bootstrap"); Objects.requireNonNull(physicalSurface, "physical route surface");
        Map<SubjectId, Map<TraversalEdgeId, TraversalAvailability>> next = new LinkedHashMap<>(supplyAvailability);
        for (Settlement settlement : bootstrap.settlements()) {
            TraversalTopology topology = supplyTraversalTopology(bootstrap, settlement.id());
            java.util.Set<TraversalEdgeId> affected = FrontierRouteNetwork.affectedTraversalEdges(topology, physicalSurface);
            if (affected.isEmpty()) continue;
            Map<TraversalEdgeId, TraversalAvailability> edges = new LinkedHashMap<>(next.getOrDefault(settlement.id(), Map.of()));
            affected.forEach(edge -> edges.put(edge, TraversalAvailability.BLOCKED));
            next.put(settlement.id(), Map.copyOf(edges));
        }
        return next.equals(supplyAvailability) ? this : new RouteTopology(replacementSupplyRoutes, next);
    }

    public boolean supplyPassable(FrontierBootstrap bootstrap, SubjectId settlementId) {
        return supplyTraversalTopology(bootstrap, settlementId).edges().stream()
                .allMatch(edge -> edge.traversableBy(TraversalCapability.PEDESTRIAN));
    }

    /** A caller must already have constructed every physical replacement cell before acceptance. */
    public RouteTopology replaceSupplyRoute(FrontierBootstrap bootstrap, SubjectId settlementId, List<BlockPosition> route) {
        FrontierRouteNetwork.validateSupplyWaypoints(bootstrap, settlementId, route);
        Map<SubjectId, List<BlockPosition>> next = new LinkedHashMap<>(replacementSupplyRoutes); next.put(settlementId, List.copyOf(route));
        Map<SubjectId, Map<TraversalEdgeId, TraversalAvailability>> nextAvailability = new LinkedHashMap<>(supplyAvailability);
        nextAvailability.remove(settlementId);
        return new RouteTopology(next, nextAvailability);
    }

    private static long contentRevision(List<BlockPosition> positions) {
        long hash = 0xcbf29ce484222325L;
        for (BlockPosition position : positions) {
            hash = mix(hash, position.x()); hash = mix(hash, position.y()); hash = mix(hash, position.z());
        }
        return hash & Long.MAX_VALUE;
    }

    private static long mix(long current, int value) { return (current ^ Integer.toUnsignedLong(value)) * 0x100000001b3L; }
}
