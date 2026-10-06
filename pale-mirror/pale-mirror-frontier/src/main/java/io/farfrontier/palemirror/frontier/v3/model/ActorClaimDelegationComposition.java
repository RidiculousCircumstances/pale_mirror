package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;
import java.util.function.BiConsumer;

/** Shared item custody validates an owner-issued delegation, without commercial dispatch logic. */
final class ActorClaimDelegationComposition {
    private static final Map<ResourceClaimDelegation.Kind, BiConsumer<FrontierWorldState, ActorContainerItemOrder>> PORTS = Map.of(
            ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT, ShipmentStateSupport::validateOrder);
    private static final Map<ResourceClaimDelegation.Kind, java.util.function.BiFunction<FrontierWorldState, ActorContainerItemOrder, ActorItemTransferPreparation>> PREPARATIONS = Map.of(
            ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT, ShipmentStateSupport::prepareTransfer);
    static {
        if (!PORTS.keySet().equals(java.util.EnumSet.allOf(ResourceClaimDelegation.Kind.class)) || !PORTS.keySet().equals(PREPARATIONS.keySet()))
            throw new IllegalArgumentException("delegated item authority lacks an exact validation/preparation owner");
    }
    static ActorItemTransferPreparation prepare(FrontierWorldState state, ActorContainerItemOrder order) {
        if (!(order.portion() instanceof ActorContainerItemOrder.Portion.Fungible portion) || portion.delegation().isEmpty())
            return ActorItemTransferPreparation.unchanged(state, order);
        var port = PREPARATIONS.get(portion.delegation().orElseThrow().kind());
        if (port == null) throw new IllegalArgumentException("unregistered delegated item preparation");
        return port.apply(state, order);
    }
    static void validate(FrontierWorldState state, ActorContainerItemOrder order) {
        if (!(order.portion() instanceof ActorContainerItemOrder.Portion.Fungible portion) || portion.delegation().isEmpty()) return;
        var port = PORTS.get(portion.delegation().orElseThrow().kind());
        if (port == null) throw new IllegalArgumentException("unregistered delegated item authority");
        port.accept(state, order);
    }
    private ActorClaimDelegationComposition() { }
}
