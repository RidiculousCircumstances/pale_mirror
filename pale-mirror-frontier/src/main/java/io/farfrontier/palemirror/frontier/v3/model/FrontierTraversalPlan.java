package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable provider-neutral traversal/port projection consumed by Foundry and physical providers. */
public record FrontierTraversalPlan(Map<TraversalTopologyId, TraversalTopology> topologies,
                                   Map<SubjectId, FacilityBinding> facilities) {
    public static final int MAX_TOPOLOGIES = 128;
    public static final int MAX_PORTS = 128;

    public FrontierTraversalPlan {
        topologies = boundedCopy(Objects.requireNonNull(topologies, "frontier traversal topologies"), MAX_TOPOLOGIES,
                "duplicate frontier topology");
        facilities = boundedCopy(Objects.requireNonNull(facilities, "frontier facility ports"), MAX_PORTS,
                "duplicate frontier facility port");
        for (Map.Entry<SubjectId, FacilityBinding> entry : facilities.entrySet()) {
            SubjectId facilityId = entry.getKey(); FacilityBinding binding = entry.getValue();
            if (!facilityId.equals(binding.port().facilityId()) || !topologies.containsKey(binding.publicTopologyId())) {
                throw new IllegalArgumentException("facility binding does not name its declared public topology");
            }
        }
    }

    public static FrontierTraversalPlan compile(FrontierWorldState state) {
        Objects.requireNonNull(state, "frontier traversal state");
        Map<TraversalTopologyId, TraversalTopology> topologies = new LinkedHashMap<>();
        Map<SubjectId, FacilityBinding> facilities = new LinkedHashMap<>();
        for (Settlement settlement : state.bootstrap().settlements()) {
            TraversalTopology supply = state.routeTopology().supplyTraversalTopology(state.bootstrap(), settlement.id());
            put(topologies, supply.id(), supply);
            TraversalTopology circulation = SettlementLocalCirculation.topology(settlement);
            put(topologies, circulation.id(), circulation);
            for (SettlementStructure structure : settlement.structures()) {
                FacilityTraversalPort port = switch (structure.kind()) {
                    case HALL -> SettlementAccessPort.forHall(structure).topologyPort();
                    case INFIRMARY -> SettlementInfirmaryTreatmentPort.forInfirmary(structure).topologyPort();
                    default -> null;
                };
                if (port == null) continue;
                TraversalTopologyId publicTopologyId = structure.kind() == StructureKind.HALL ? supply.id() : circulation.id();
                facilities.put(port.facilityId(), new FacilityBinding(settlement.id(), port, publicTopologyId));
                TraversalTopology ingress = port.ingressTopology(new TraversalTopologyId("topology:port-" + port.facilityId().value().replace(':', '-')),
                        portRevision(port), structure.id());
                put(topologies, ingress.id(), ingress);
            }
        }
        return new FrontierTraversalPlan(topologies, facilities);
    }

    /** The named public topology a port must join, never a runtime nearest-route search. */
    public TraversalTopology publicTopologyFor(SubjectId facilityId) {
        FacilityBinding binding = facilities.get(Objects.requireNonNull(facilityId, "facility id"));
        if (binding == null) throw new IllegalArgumentException("frontier traversal plan has no facility: " + facilityId);
        return topologies.get(binding.publicTopologyId());
    }

    public TraversalTopology topology(String id) {
        TraversalTopology topology = topologies.get(new TraversalTopologyId(id));
        if (topology == null) throw new IllegalArgumentException("frontier traversal plan has no topology: " + id);
        return topology;
    }

    private static <K, V> Map<K, V> boundedCopy(Map<K, V> values, int maximum, String duplicateMessage) {
        if (values.size() > maximum) throw new IllegalArgumentException("frontier traversal plan limit exceeded");
        Map<K, V> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (copy.put(Objects.requireNonNull(key, "frontier traversal key"), Objects.requireNonNull(value, "frontier traversal value")) != null) {
                throw new IllegalArgumentException(duplicateMessage);
            }
        });
        // These maps define diagnostic/physical scan order.  Keep the compiler's deterministic
        // insertion order instead of relying on Map.copyOf's deliberately unspecified order.
        return Collections.unmodifiableMap(copy);
    }

    private static void put(Map<TraversalTopologyId, TraversalTopology> values, TraversalTopologyId id, TraversalTopology topology) {
        if (values.put(id, topology) != null) throw new IllegalArgumentException("frontier traversal plan topology collision: " + id);
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
    public record FacilityBinding(SubjectId settlementId, FacilityTraversalPort port,
                                  TraversalTopologyId publicTopologyId) {
        public FacilityBinding {
            settlementId = Objects.requireNonNull(settlementId, "facility binding settlement");
            port = Objects.requireNonNull(port, "facility binding port");
            publicTopologyId = Objects.requireNonNull(publicTopologyId, "facility binding public topology");
        }
    }
}
