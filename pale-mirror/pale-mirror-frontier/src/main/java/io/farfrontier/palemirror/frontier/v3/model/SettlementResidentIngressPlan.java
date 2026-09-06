package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable public residential apron and its one terrain-aware settlement ingress.
 *
 * <p>Residents belong on supported settlement ground, not on an accidental natural-height
 * perimeter around an elevated facility deck.  This plan gives every possible housing bed a
 * deterministic, materialized home surface on a bounded public apron.  The apron joins the
 * Hall's semantic route port and continues through a short grade-checked stair to a surveyed
 * natural support column.  It is a plan compiler: it neither asks Minecraft for a height nor
 * grants a navigator an alternate entrance.</p>
 */
public final class SettlementResidentIngressPlan {
    private static final int WEST_EAST_RADIUS = 30;
    private static final int NORTH_SOUTH_RADIUS = 30;
    private static final int MAX_HOUSING_BEDS = 96;
    private static final int MAX_INGRESS_LENGTH = 32;

    private SettlementResidentIngressPlan() { }

    public static Plan compile(WorldBounds bounds, TerrainSurfacePlan terrain, Settlement settlement, int housingBeds) {
        Objects.requireNonNull(bounds, "resident ingress bounds");
        Objects.requireNonNull(terrain, "resident ingress terrain");
        Objects.requireNonNull(settlement, "resident ingress settlement");
        return compile(bounds, terrain, settlement.id(), settlement.anchor(), settlement.structures(), housingBeds);
    }

    static Plan compile(WorldBounds bounds, TerrainSurfacePlan terrain, SubjectId settlementId, BlockPosition settlementAnchor,
                        List<SettlementStructure> structures, int housingBeds) {
        Objects.requireNonNull(bounds, "resident ingress bounds");
        Objects.requireNonNull(terrain, "resident ingress terrain");
        Objects.requireNonNull(settlementId, "resident ingress settlement");
        Objects.requireNonNull(settlementAnchor, "resident ingress anchor");
        structures = List.copyOf(Objects.requireNonNull(structures, "resident ingress structures"));
        if (housingBeds <= 0 || housingBeds > MAX_HOUSING_BEDS) {
            throw new IllegalArgumentException("resident ingress housing beds must be in 1.." + MAX_HOUSING_BEDS);
        }

        SettlementStructure hall = structures.stream().filter(structure -> structure.kind() == StructureKind.HALL)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("resident ingress requires a Hall"));
        SettlementAccessPort access = SettlementAccessPort.forHall(hall);
        Set<BlockPosition> structureCells = FrontierSettlementActorSlots.intactStructureOccupancy(terrain, structures);
        List<SurfaceAnchor> apron = perimeter(bounds, settlementAnchor, structureCells);
        SurfaceAnchor port = access.routeSurface();
        SurfaceAnchor apronGate = gate(settlementAnchor, hall.facing());
        List<SurfaceAnchor> connector = connector(bounds, port, apronGate, hall.facing(), structureCells);
        List<SurfaceAnchor> ramp = ingressRamp(bounds, terrain, settlementId, apronGate, hall.facing(), structureCells);

        LinkedHashSet<SurfaceAnchor> owned = new LinkedHashSet<>(apron);
        // The Hall route surface is route-owned; every following corridor cell is settlement-owned.
        owned.addAll(connector.subList(1, connector.size()));
        owned.addAll(ramp.subList(1, ramp.size()));
        if (owned.size() != apron.size() + connector.size() - 2 + ramp.size() - 1) {
            throw new IllegalArgumentException("resident ingress plan has an unintended surface overlap");
        }
        owned.forEach(surface -> validateOwnedSurface(bounds, terrain, structureCells, surface));

        List<BlockPosition> homes = homeSlots(apron, housingBeds);
        TraversalTopology topology = topology(settlementId, port, apron, connector, ramp);
        return new Plan(homes, immutableSet(owned), foundationCells(terrain, owned), topology);
    }

    private static List<SurfaceAnchor> perimeter(WorldBounds bounds, BlockPosition anchor, Set<BlockPosition> structureCells) {
        int minX = anchor.x() - WEST_EAST_RADIUS, maxX = anchor.x() + WEST_EAST_RADIUS;
        int minZ = anchor.z() - NORTH_SOUTH_RADIUS, maxZ = anchor.z() + NORTH_SOUTH_RADIUS;
        List<SurfaceAnchor> result = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) addPerimeter(result, bounds, structureCells, SurfaceAnchor.at(x, anchor.y(), minZ));
        for (int z = minZ + 1; z <= maxZ; z++) addPerimeter(result, bounds, structureCells, SurfaceAnchor.at(maxX, anchor.y(), z));
        for (int x = maxX - 1; x >= minX; x--) addPerimeter(result, bounds, structureCells, SurfaceAnchor.at(x, anchor.y(), maxZ));
        for (int z = maxZ - 1; z > minZ; z--) addPerimeter(result, bounds, structureCells, SurfaceAnchor.at(minX, anchor.y(), z));
        return List.copyOf(result);
    }

    private static void addPerimeter(List<SurfaceAnchor> result, WorldBounds bounds, Set<BlockPosition> structureCells, SurfaceAnchor surface) {
        if (!bounds.contains(surface.support()) || structureCells.contains(surface.support())
                || structureCells.contains(surface.support().offset(0, 1, 0)) || structureCells.contains(surface.support().offset(0, 2, 0))) {
            throw new IllegalArgumentException("resident apron does not have a clear bounded surface at " + surface.support());
        }
        result.add(surface);
    }

    private static SurfaceAnchor gate(BlockPosition anchor, FacilityFacing facing) {
        int radius = facing.x() == 0 ? NORTH_SOUTH_RADIUS : WEST_EAST_RADIUS;
        return SurfaceAnchor.at(anchor.x() + facing.x() * radius, anchor.y(), anchor.z() + facing.z() * radius);
    }

    private static List<SurfaceAnchor> connector(WorldBounds bounds, SurfaceAnchor port, SurfaceAnchor gate, FacilityFacing facing,
                                                  Set<BlockPosition> structureCells) {
        List<SurfaceAnchor> result = new ArrayList<>();
        result.add(port);
        int x = port.x(), z = port.z();
        while (x != gate.x() || z != gate.z()) {
            x += facing.x(); z += facing.z();
            SurfaceAnchor surface = SurfaceAnchor.at(x, port.y(), z);
            if (!bounds.contains(surface.support()) || structureCells.contains(surface.support())
                    || structureCells.contains(surface.support().offset(0, 1, 0)) || structureCells.contains(surface.support().offset(0, 2, 0))) {
                throw new IllegalArgumentException("resident apron connector is obstructed at " + surface.support());
            }
            result.add(surface);
        }
        if (result.size() < 2) throw new IllegalArgumentException("resident apron connector must leave the Hall route surface");
        return List.copyOf(result);
    }

    private static List<SurfaceAnchor> ingressRamp(WorldBounds bounds, TerrainSurfacePlan terrain, SubjectId settlementId,
                                                    SurfaceAnchor gate, FacilityFacing facing, Set<BlockPosition> structureCells) {
        for (int length = 1; length <= MAX_INGRESS_LENGTH; length++) {
            int endX = gate.x() + facing.x() * length, endZ = gate.z() + facing.z() * length;
            int endY = Math.addExact(terrain.supportYAt(endX, endZ), 1);
            if (Math.abs(endY - gate.y()) > length) continue;
            List<SurfaceAnchor> candidate = new ArrayList<>(); candidate.add(gate);
            boolean valid = true;
            for (int step = 1; step <= length; step++) {
                int x = gate.x() + facing.x() * step, z = gate.z() + facing.z() * step;
                int y = gate.y() + Math.floorDiv((endY - gate.y()) * step, length);
                SurfaceAnchor surface = SurfaceAnchor.at(x, y, z);
                if (!bounds.contains(surface.support()) || surface.y() <= terrain.supportYAt(x, z)
                        || structureCells.contains(surface.support()) || structureCells.contains(surface.support().offset(0, 1, 0))
                        || structureCells.contains(surface.support().offset(0, 2, 0))
                        || Math.abs(surface.y() - candidate.getLast().y()) > 1) {
                    valid = false; break;
                }
                candidate.add(surface);
            }
            if (valid) return List.copyOf(candidate);
        }
        throw new IllegalArgumentException("resident ingress cannot reach surveyed natural support within " + MAX_INGRESS_LENGTH
                + " cells for " + settlementId);
    }

    private static void validateOwnedSurface(WorldBounds bounds, TerrainSurfacePlan terrain, Set<BlockPosition> structureCells, SurfaceAnchor surface) {
        if (!bounds.contains(surface.support()) || surface.y() <= terrain.supportYAt(surface.x(), surface.z())
                || structureCells.contains(surface.support()) || structureCells.contains(surface.support().offset(0, 1, 0))
                || structureCells.contains(surface.support().offset(0, 2, 0))) {
            throw new IllegalArgumentException("resident ingress owns an invalid support surface at " + surface.support());
        }
    }

    private static List<BlockPosition> homeSlots(List<SurfaceAnchor> apron, int housingBeds) {
        List<BlockPosition> homes = new ArrayList<>(housingBeds);
        for (int index = 0; index < apron.size() && homes.size() < housingBeds; index += 2) homes.add(apron.get(index).support());
        if (homes.size() != housingBeds) throw new IllegalArgumentException("resident apron cannot hold requested housing beds");
        return List.copyOf(homes);
    }

    private static Set<BlockPosition> foundationCells(TerrainSurfacePlan terrain, Set<SurfaceAnchor> surfaces) {
        LinkedHashSet<BlockPosition> fill = new LinkedHashSet<>();
        for (SurfaceAnchor surface : surfaces) {
            for (int y = terrain.supportYAt(surface.x(), surface.z()) + 1; y < surface.y(); y++) {
                fill.add(new BlockPosition(surface.x(), y, surface.z()));
            }
        }
        return immutableSet(fill);
    }

    private static TraversalTopology topology(SubjectId settlementId, SurfaceAnchor port, List<SurfaceAnchor> apron,
                                              List<SurfaceAnchor> connector, List<SurfaceAnchor> ramp) {
        Map<TraversalNodeId, SurfaceAnchor> nodes = new LinkedHashMap<>();
        Map<String, TraversalTopology.Edge> edges = new LinkedHashMap<>();
        long revision = revision(port, apron, connector, ramp);
        addBranch(nodes, edges, connector, revision, false);
        addBranch(nodes, edges, apron, revision, true);
        addBranch(nodes, edges, ramp, revision, false);
        return new TraversalTopology(new TraversalTopologyId("topology:resident-ingress-" + settlementId.value().substring("settlement:".length())),
                revision, settlementId, nodes, List.copyOf(edges.values()));
    }

    private static void addBranch(Map<TraversalNodeId, SurfaceAnchor> nodes, Map<String, TraversalTopology.Edge> edges,
                                  List<SurfaceAnchor> branch, long revision, boolean close) {
        for (int index = 0; index < branch.size(); index++) {
            SurfaceAnchor current = branch.get(index); TraversalNodeId node = node(current); nodes.putIfAbsent(node, current);
            if (index > 0) addBidirectional(edges, node(branch.get(index - 1)), node, branch.get(index - 1), current, revision);
        }
        if (close) addBidirectional(edges, node(branch.getLast()), node(branch.getFirst()), branch.getLast(), branch.getFirst(), revision);
    }

    private static void addBidirectional(Map<String, TraversalTopology.Edge> edges, TraversalNodeId from, TraversalNodeId to,
                                         SurfaceAnchor fromSurface, SurfaceAnchor toSurface, long revision) {
        addEdge(edges, from, to, fromSurface, toSurface, revision);
        addEdge(edges, to, from, toSurface, fromSurface, revision);
    }

    private static void addEdge(Map<String, TraversalTopology.Edge> edges, TraversalNodeId from, TraversalNodeId to,
                                SurfaceAnchor fromSurface, SurfaceAnchor toSurface, long revision) {
        String key = from.value() + ">" + to.value();
        edges.putIfAbsent(key, new TraversalTopology.Edge(new TraversalEdgeId("edge:" + key), from, to, TraversalKind.PEDESTRIAN,
                Set.of(TraversalCapability.PEDESTRIAN), Math.abs(fromSurface.y() - toSurface.y()), 2, revision, TraversalAvailability.OPEN));
    }

    private static TraversalNodeId node(SurfaceAnchor surface) {
        return new TraversalNodeId("node:" + surface.x() + ":" + surface.y() + ":" + surface.z());
    }

    private static long revision(SurfaceAnchor port, List<SurfaceAnchor> apron, List<SurfaceAnchor> connector, List<SurfaceAnchor> ramp) {
        long hash = 0xcbf29ce484222325L;
        List<SurfaceAnchor> all = new ArrayList<>(); all.add(port); all.addAll(apron); all.addAll(connector); all.addAll(ramp);
        for (SurfaceAnchor surface : all) {
            hash = (hash ^ Integer.toUnsignedLong(surface.x())) * 0x100000001b3L;
            hash = (hash ^ Integer.toUnsignedLong(surface.y())) * 0x100000001b3L;
            hash = (hash ^ Integer.toUnsignedLong(surface.z())) * 0x100000001b3L;
        }
        return hash & Long.MAX_VALUE;
    }

    private static <T> Set<T> immutableSet(Set<T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    public record Plan(List<BlockPosition> homeSlots, Set<SurfaceAnchor> ownedSurfaces, Set<BlockPosition> foundationCells,
                       TraversalTopology topology) {
        public Plan {
            homeSlots = List.copyOf(homeSlots); ownedSurfaces = immutableSet(ownedSurfaces); foundationCells = immutableSet(foundationCells);
            topology = Objects.requireNonNull(topology, "resident ingress topology");
            if (homeSlots.isEmpty() || ownedSurfaces.isEmpty() || topology.nodes().isEmpty()) {
                throw new IllegalArgumentException("resident ingress plan must retain homes, surfaces and topology");
            }
        }
    }
}
