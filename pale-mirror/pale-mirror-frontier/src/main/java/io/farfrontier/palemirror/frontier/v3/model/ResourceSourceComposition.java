package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Closed source capability composition. Logistics asks for geometry/evidence, not physical-source details. */
public final class ResourceSourceComposition {
    private static final Map<ResourceSourceEndpoint.Kind, ResourceSourcePort> PORTS = List.of(
            new ContainerResourceSourcePort(), new GroundResourceSourcePort()).stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(ResourceSourcePort::kind, port -> port));
    static {
        if (!PORTS.keySet().equals(EnumSet.allOf(ResourceSourceEndpoint.Kind.class)))
            throw new IllegalArgumentException("missing resource source provider");
    }
    private ResourceSourceComposition() { }
    private static ResourceSourcePort port(ResourceSourceEndpoint source) {
        Objects.requireNonNull(source);
        var port = PORTS.get(source.sourceKind());
        if (port == null) throw new IllegalArgumentException("unknown declared resource source capability");
        return port;
    }
    public static void validate(FrontierWorldState state, ResourceSourceEndpoint source) {
        port(source).validate(state, source);
    }
    public static void admit(FrontierWorldState state, ResourceSourceEndpoint source) {
        port(source).admit(state, source);
    }
    public static ShipmentEndpoint requireContainer(ResourceSourceEndpoint source) {
        if (source.sourceKind() != ResourceSourceEndpoint.Kind.CONTAINER || !(source instanceof ShipmentEndpoint endpoint))
            throw new IllegalArgumentException("operation requires an explicitly declared container source");
        return endpoint;
    }
    public static KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, ResourceSourceEndpoint source, ShipmentEndpoint receiver) {
        validate(state, source);
        return port(source).knowledge(state, source, receiver);
    }
    public static boolean coldAvailable(FrontierWorldState state, ResourceSourceEndpoint source) {
        return port(source).coldAvailable(state, source);
    }
    public static ActorResourceItemOrder pickup(ResourceSourceEndpoint source,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId owner, io.farfrontier.palemirror.frontier.v3.api.SubjectId actor,
            ActorContainerItemOrder.Portion.Fungible portion, ActorItemSlot slot, long ordinal, long revision) {
        return port(source).pickup(source, owner, actor, portion, slot, ordinal, revision);
    }
}
