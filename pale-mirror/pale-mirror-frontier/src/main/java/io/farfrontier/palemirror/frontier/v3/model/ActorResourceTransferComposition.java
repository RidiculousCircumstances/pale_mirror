package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Closed endpoint adapters. The common work/haul caller does not distinguish a chest from a ground pickup. */
public final class ActorResourceTransferComposition {
    private record Evidence(long sourceEpoch, long destinationEpoch,
                            List<FungiblePhysicalObservation.Stack> source, List<FungiblePhysicalObservation.Stack> destination) { }
    private interface Port {
        FrontierWorldStateUpdate cold(FrontierWorldState state, ActorResourceItemOrder order);
        FrontierWorldStateUpdate observed(FrontierWorldState state, ActorResourceItemOrder order, Evidence evidence);
    }
    private static final Map<ActorResourceItemOrder.EndpointKind, Port> PORTS = Map.of(
            ActorResourceItemOrder.EndpointKind.CONTAINER, new Port() {
                private ActorContainerItemOrder require(ActorResourceItemOrder order) {
                    if (!(order instanceof ActorContainerItemOrder container)) throw new IllegalArgumentException("foreign container transfer schema");
                    return container;
                }
                @Override public FrontierWorldStateUpdate cold(FrontierWorldState state, ActorResourceItemOrder order) {
                    return ActorItemCustody.transferColdUpdate(state, require(order));
                }
                @Override public FrontierWorldStateUpdate observed(FrontierWorldState state, ActorResourceItemOrder order, Evidence evidence) {
                    return ActorItemCustody.transferObservedUpdate(state, require(order), evidence.sourceEpoch(), evidence.destinationEpoch(), evidence.source(), evidence.destination());
                }
            },
            ActorResourceItemOrder.EndpointKind.GROUND_ITEM, new Port() {
                @Override public FrontierWorldStateUpdate cold(FrontierWorldState state, ActorResourceItemOrder order) {
                    throw new IllegalArgumentException("ground pickup retains native item custody; an observed transfer is required");
                }
                @Override public FrontierWorldStateUpdate observed(FrontierWorldState state, ActorResourceItemOrder order, Evidence evidence) {
                    if (!(order instanceof ActorGroundItemOrder ground)) throw new IllegalArgumentException("foreign ground transfer schema");
                    return ActorItemCustody.transferObservedUpdate(state, ground, evidence.sourceEpoch(), evidence.destinationEpoch(), evidence.source(), evidence.destination());
                }
            });
    static {
        if (!PORTS.keySet().equals(EnumSet.allOf(ActorResourceItemOrder.EndpointKind.class)))
            throw new IllegalArgumentException("missing actor resource endpoint adapter");
    }
    private ActorResourceTransferComposition() { }
    private static Port port(ActorResourceItemOrder order) {
        var port = PORTS.get(Objects.requireNonNull(order).endpointKind());
        if (port == null) throw new IllegalArgumentException("unknown actor resource endpoint capability");
        return port;
    }
    public static FrontierWorldStateUpdate cold(FrontierWorldState state, ActorResourceItemOrder order) { return port(order).cold(state, order); }
    public static FrontierWorldStateUpdate observed(FrontierWorldState state, ActorResourceItemOrder order, long sourceEpoch,
            long destinationEpoch, List<FungiblePhysicalObservation.Stack> remainingSource, List<FungiblePhysicalObservation.Stack> destination) {
        return port(order).observed(state, order, new Evidence(sourceEpoch, destinationEpoch, List.copyOf(remainingSource), List.copyOf(destination)));
    }
}
