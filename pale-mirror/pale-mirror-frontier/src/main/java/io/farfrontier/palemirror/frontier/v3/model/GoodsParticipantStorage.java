package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Market inventory scope. Discovery never changes an admitted order, contract or shipment endpoint. */
public final class GoodsParticipantStorage {
    private static final Set<ResourceStorageRole> ROLES = Set.of(ResourceStorageRole.WAREHOUSE);
    private GoodsParticipantStorage() { }

    public static Set<SubjectId> declared(FrontierWorldState state, GoodsParticipant participant) {
        var result = new HashSet<>(ResourceStorageSelection.declaredContainers(state.inventory(), participant.settlementId(), ROLES));
        if (!participant.party().id().equals(participant.settlementId()))
            result.addAll(ResourceStorageSelection.declaredContainers(state.inventory(), participant.party().id(), ROLES));
        return Set.copyOf(result);
    }
    public static Set<SubjectId> containers(FrontierWorldState state, GoodsParticipant participant, String kind) {
        var result = new HashSet<>(ResourceStorageSelection.containers(state, participant.settlementId(), kind, ROLES));
        if (!participant.party().id().equals(participant.settlementId()))
            result.addAll(ResourceStorageSelection.containers(state, participant.party().id(), kind, ROLES));
        return Set.copyOf(result);
    }
    /** Resolve an exact previously selected container declaration, including a retired one needed for disposition. */
    public static ShipmentEndpoint endpoint(FrontierWorldState state, SubjectId container) {
        var record = state.inventory().containers().get(container);
        if (record == null) throw new IllegalArgumentException("commercial storage identity is missing: " + container);
        var endpoint = record.storage().orElseThrow(() -> new IllegalArgumentException("commercial container has no storage declaration")).endpoint();
        ShipmentEndpointComposition.validate(state, endpoint);
        return endpoint;
    }
    public static boolean eligible(FrontierWorldState state, GoodsParticipant participant, SubjectId container, String kind) {
        return containers(state, participant, kind).contains(container);
    }
    public static int unclaimed(FrontierWorldState state, GoodsParticipant participant, SubjectId container, String kind) {
        if (GoodsStockSpecification.forKind(state.bootstrap().ruleset().goodsTrade(), kind).representation()
                == GoodsStockSpecification.Representation.INDIVIDUAL)
            return individualStock(state, participant, container, kind).size();
        return FungibleResourceCustodySupport.accountAtContainer(state, container).map(account ->
                state.inventory().fungibleResources().unclaimedQuantity(account.id(), participant.party().id(), kind)).orElse(0);
    }
    public static List<ExactItemStack> individualStock(FrontierWorldState state, GoodsParticipant participant, SubjectId container, String kind) {
        var specification = GoodsStockSpecification.forKind(state.bootstrap().ruleset().goodsTrade(), kind);
        var claimed = WorkEquipmentClaimComposition.reserved(state);
        return ExactItemReadIndex.owned(state.inventory(), participant.party().id(), kind).stream().filter(item -> item.economicOwnerId().equals(participant.party().id())
                && item.itemKind().equals(kind) && item.custody() instanceof InventoryCustody.ContainerSlot slot
                && slot.containerId().equals(container) && specification.accepts(item) && !claimed.contains(item.id()))
                .sorted(Comparator.comparingInt((ExactItemStack item) -> item.condition().damage()).thenComparing(ExactItemStack::id)).toList();
    }
    public static int unclaimed(FrontierWorldState state, GoodsParticipant participant, String kind) {
        return containers(state, participant, kind).stream().mapToInt(container -> unclaimed(state, participant, container, kind))
                .reduce(0, Math::addExact);
    }
    /** Alternative exact quote locations, not independent stock budgets. Matching rechecks the shared intent. */
    public static List<ShipmentEndpoint> quoteEndpoints(FrontierWorldState state, GoodsParticipant participant,
            GoodsParticipantPolicy.Intent intent) {
        return containers(state, participant, intent.itemKind()).stream()
                .filter(id -> intent.side() == GoodsTradeOrder.Side.SELL
                        ? unclaimed(state, participant, id, intent.itemKind()) > 0
                        : GoodsTradeStateSupport.receivingCapacity(state, id, intent.itemKind(), 1))
                .sorted(Comparator.<SubjectId>comparingInt(id -> intent.side() == GoodsTradeOrder.Side.SELL
                        ? unclaimed(state, participant, id, intent.itemKind()) : 0).reversed().thenComparing(Comparator.naturalOrder()))
                .map(id -> endpoint(state, id)).toList();
    }
}
