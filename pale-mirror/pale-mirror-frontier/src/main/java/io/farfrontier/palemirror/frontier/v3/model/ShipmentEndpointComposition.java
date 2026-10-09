package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Facility capabilities validate a declared physical station; carriers do not guess geometry. */
public final class ShipmentEndpointComposition {
    private static final List<ShipmentEndpointPort> PROVIDERS = List.of(new DepotShipmentEndpointPort(), new ExtractiveShipmentEndpointPort());
    private static final Map<ShipmentEndpoint.Kind, ShipmentEndpointPort> PORTS = PROVIDERS.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(ShipmentEndpointPort::kind, value -> value));
    static {
        if (!PORTS.keySet().equals(EnumSet.allOf(ShipmentEndpoint.Kind.class)))
            throw new IllegalArgumentException("missing shipment endpoint capability");
    }
    private static ShipmentEndpointPort port(ShipmentEndpoint endpoint) {
        var port = PORTS.get(endpoint.kind());
        if (port == null) throw new IllegalArgumentException("unregistered shipment endpoint provider");
        return port;
    }
    public static void validate(FrontierWorldState state, ShipmentEndpoint endpoint) {
        port(endpoint).validate(state, endpoint);
    }
    public static KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, List<ShipmentEndpoint> endpoints) {
        endpoints = List.copyOf(endpoints);
        if (endpoints.isEmpty() || endpoints.size() > 8) throw new IllegalArgumentException("shipment journey needs bounded endpoints");
        var passages = endpoints.stream().flatMap(endpoint -> port(endpoint).passages(state, endpoint).stream()).distinct().toList();
        return passages.isEmpty() ? KnownPedestrianRouteKnowledge.forFrontier(state) : KnownPedestrianRouteKnowledge.forJourney(state, passages);
    }
    public static SurfaceAnchor exteriorApproach(FrontierWorldState state, ShipmentEndpoint endpoint) {
        return port(endpoint).exteriorApproach(state, endpoint);
    }
    private ShipmentEndpointComposition() { }
}
