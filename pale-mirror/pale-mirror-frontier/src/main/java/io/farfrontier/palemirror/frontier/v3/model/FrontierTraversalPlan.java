package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable provider-neutral traversal/port projection consumed by Foundry and physical providers.
 */
public record FrontierTraversalPlan(
        Map<TraversalTopologyId, TraversalTopology> topologies,
        Map<SubjectId, FacilityBinding> facilities) {
    public static final int MAX_TOPOLOGIES =
            SimulationCapacity.MAX_SETTLEMENTS * (StructureKind.values().length * 2 + 3);
    public static final int MAX_PORTS =
            SimulationCapacity.MAX_SETTLEMENTS * StructureKind.values().length;
    private static final int MAX_PORT_VIEWS = 4_096;
    private static final Map<SettlementStructure, Optional<FacilityTraversalPort>> PORT_VIEWS =
            new LinkedHashMap<>(128, .75f, true);

    public FrontierTraversalPlan {
        topologies =
                boundedCopy(
                        Objects.requireNonNull(topologies, "frontier traversal topologies"),
                        MAX_TOPOLOGIES,
                        "duplicate frontier topology");
        facilities =
                boundedCopy(
                        Objects.requireNonNull(facilities, "frontier facility ports"),
                        MAX_PORTS,
                        "duplicate frontier facility port");
        for (Map.Entry<SubjectId, FacilityBinding> entry : facilities.entrySet()) {
            SubjectId facilityId = entry.getKey();
            FacilityBinding binding = entry.getValue();
            if (!facilityId.equals(binding.port().facilityId())
                    || !topologies.containsKey(binding.publicTopologyId())) {
                throw new IllegalArgumentException(
                        "facility binding does not name its declared public topology");
            }
        }
    }

    public static FrontierTraversalPlan compile(FrontierWorldState state) {
        Objects.requireNonNull(state, "frontier traversal state");
        Map<TraversalTopologyId, TraversalTopology> topologies = new LinkedHashMap<>();
        Map<SubjectId, FacilityBinding> facilities = new LinkedHashMap<>();
        for (Settlement settlement : state.bootstrap().settlements()) {
            TraversalTopology supply =
                    state.routeTopology()
                            .settlementTraversalTopology(state.bootstrap(), settlement.id());
            put(topologies, supply.id(), supply);
            TraversalTopology circulation = SettlementLocalCirculation.topology(settlement);
            put(topologies, circulation.id(), circulation);
            SettlementResidentIngressPlan.Plan residentIngress =
                    SettlementResidentIngressPlan.compile(
                            state.bootstrap().bounds(),
                            state.bootstrap().terrain(),
                            settlement,
                            state.bootstrap().ruleset().facilityCapacity().intactHousingBeds());
            put(topologies, residentIngress.topology().id(), residentIngress.topology());
            for (SettlementStructure structure : settlement.structures()) {
                FacilityTraversalPort port = facilityPort(structure).orElse(null);
                if (port == null) continue;
                TraversalTopologyId publicTopologyId =
                        structure.kind() == StructureKind.HALL ? supply.id() : circulation.id();
                facilities.put(
                        port.facilityId(),
                        new FacilityBinding(
                                settlement.id(), port, publicTopologyId, facilityPorts(structure)));
                int index = 0;
                for (var entry : facilityPorts(structure)) {
                    TraversalTopology ingress =
                            entry.ingressTopology(
                                    new TraversalTopologyId(
                                            "topology:port-"
                                                    + entry.facilityId().value().replace(':', '-')
                                                    + (index++ == 0 ? "" : "-" + index)),
                                    portRevision(entry),
                                    structure.id());
                    put(topologies, ingress.id(), ingress);
                }
            }
        }
        return new FrontierTraversalPlan(topologies, facilities);
    }

    /**
     * The structure plan's one typed pedestrian entrance, shared by topology and route knowledge.
     */
    public static Optional<FacilityTraversalPort> facilityPort(SettlementStructure structure) {
        Objects.requireNonNull(structure, "facility port structure");
        synchronized (PORT_VIEWS) {
            var prior = PORT_VIEWS.get(structure);
            if (prior != null) return prior;
        }
        var result =
                structure.definition().representation()
                                == io.farfrontier.palemirror.frontier.v3.model.buildings
                                        .BuildingDefinition.Representation.AUTHORED
                        ? Optional.of(
                                io.farfrontier.palemirror.frontier.v3.model.buildings
                                        .BuildingSpatialPlan.of(structure)
                                        .primaryEntrance())
                        : Optional.ofNullable(
                                switch (structure.kind()) {
                                    case HALL ->
                                            SettlementAccessPort.forHall(structure).topologyPort();
                                    case DEPOT ->
                                            SettlementDepotServicePort.forDepot(structure)
                                                    .topologyPort();
                                    case WORKSHOP, SMELTERY, FORGE, WOODSHOP ->
                                            SettlementWorkshopServicePort.forWorkshop(structure)
                                                    .topologyPort();
                                    case INFIRMARY ->
                                            SettlementInfirmaryTreatmentPort.forInfirmary(structure)
                                                    .topologyPort();
                                    default -> null;
                                });
        synchronized (PORT_VIEWS) {
            var prior = PORT_VIEWS.get(structure);
            if (prior != null) return prior;
            if (PORT_VIEWS.size() >= MAX_PORT_VIEWS)
                PORT_VIEWS.remove(PORT_VIEWS.keySet().iterator().next());
            PORT_VIEWS.put(structure, result);
            return result;
        }
    }

    /** All declared entrances share the same facility identity and current geometric truth. */
    public static java.util.List<FacilityTraversalPort> facilityPorts(
            SettlementStructure structure) {
        if (structure.definition().representation()
                == io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingDefinition
                        .Representation.AUTHORED)
            return java.util.List.copyOf(
                    io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingSpatialPlan.of(
                                    structure)
                            .entrances()
                            .values());
        return facilityPort(structure).stream().toList();
    }

    /** The named public topology a port must join, never a runtime nearest-route search. */
    public TraversalTopology publicTopologyFor(SubjectId facilityId) {
        FacilityBinding binding = facilities.get(Objects.requireNonNull(facilityId, "facility id"));
        if (binding == null)
            throw new IllegalArgumentException(
                    "frontier traversal plan has no facility: " + facilityId);
        return topologies.get(binding.publicTopologyId());
    }

    public TraversalTopology topology(String id) {
        TraversalTopology topology = topologies.get(new TraversalTopologyId(id));
        if (topology == null)
            throw new IllegalArgumentException("frontier traversal plan has no topology: " + id);
        return topology;
    }

    private static <K, V> Map<K, V> boundedCopy(
            Map<K, V> values, int maximum, String duplicateMessage) {
        if (values.size() > maximum)
            throw new IllegalArgumentException("frontier traversal plan limit exceeded");
        Map<K, V> copy = new LinkedHashMap<>();
        values.forEach(
                (key, value) -> {
                    if (copy.put(
                                    Objects.requireNonNull(key, "frontier traversal key"),
                                    Objects.requireNonNull(value, "frontier traversal value"))
                            != null) {
                        throw new IllegalArgumentException(duplicateMessage);
                    }
                });
        // These maps define diagnostic/physical scan order.  Keep the compiler's deterministic
        // insertion order instead of relying on Map.copyOf's deliberately unspecified order.
        return Collections.unmodifiableMap(copy);
    }

    private static void put(
            Map<TraversalTopologyId, TraversalTopology> values,
            TraversalTopologyId id,
            TraversalTopology topology) {
        if (values.put(id, topology) != null)
            throw new IllegalArgumentException("frontier traversal plan topology collision: " + id);
    }

    private static long portRevision(FacilityTraversalPort port) {
        long hash = 0xcbf29ce484222325L;
        for (SurfaceAnchor surface : port.ingressSurfaces()) {
            hash = (hash ^ Integer.toUnsignedLong(surface.x())) * 0x100000001b3L;
            hash = (hash ^ Integer.toUnsignedLong(surface.y())) * 0x100000001b3L;
            hash = (hash ^ Integer.toUnsignedLong(surface.z())) * 0x100000001b3L;
        }
        return hash & Long.MAX_VALUE;
    }

    /** Facility relation to the one immutable public network it is allowed to join. */
    public record FacilityBinding(
            SubjectId settlementId,
            FacilityTraversalPort port,
            TraversalTopologyId publicTopologyId,
            java.util.List<FacilityTraversalPort> ports) {
        public FacilityBinding(
                SubjectId settlementId,
                FacilityTraversalPort port,
                TraversalTopologyId publicTopologyId) {
            this(settlementId, port, publicTopologyId, java.util.List.of(port));
        }

        public FacilityBinding {
            ports = java.util.List.copyOf(ports);
            var primaryId = Objects.requireNonNull(port).facilityId();
            if (ports.isEmpty()
                    || !ports.contains(port)
                    || ports.stream().anyMatch(p -> !p.facilityId().equals(primaryId)))
                throw new IllegalArgumentException(
                        "facility alternatives lack their explicit primary/owner");
            settlementId = Objects.requireNonNull(settlementId, "facility binding settlement");
            port = Objects.requireNonNull(port, "facility binding port");
            publicTopologyId =
                    Objects.requireNonNull(publicTopologyId, "facility binding public topology");
        }
    }
}
