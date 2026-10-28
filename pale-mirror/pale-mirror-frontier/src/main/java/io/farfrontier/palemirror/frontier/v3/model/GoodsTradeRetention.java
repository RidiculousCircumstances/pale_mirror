package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Bounded commercial history lifecycle; unrelated public fills never form a retirement component. */
public final class GoodsTradeRetention {
    private static final int MAX_PER_REVIEW = 32;
    private static final ImmutableInputView<Set<SubjectId>> PHYSICAL_REFERENCES = new ImmutableInputView<>();
    private static final ImmutableInputView<Set<SubjectId>> SHIPMENT_OWNERS = new ImmutableInputView<>();
    private GoodsTradeRetention() { }
    public static Optional<GoodsTradeRetired> collect(FrontierWorldState state, long now) {
        var trade = state.companies().goodsTrade();
        if (trade.orders().isEmpty() && trade.contracts().isEmpty()) return Optional.empty();
        // Counterfactual market candidates change commercial state, not these
        // immutable obligations. Read each complete source once per input image.
        var physical = PHYSICAL_REFERENCES.get(List.of(state.physicalIntents()), () -> {
            var subjects = new HashSet<SubjectId>();
            state.physicalIntents().values().forEach(intent -> {
                subjects.add(intent.causeSubjectId()); subjects.addAll(intent.roles().namedRoles().values());
            });
            return Set.copyOf(subjects);
        });
        var shipmentOwners = SHIPMENT_OWNERS.get(List.of(state.shipments().shipments()), () -> {
            var owners = new HashSet<SubjectId>();
            state.shipments().shipments().values().stream()
                    .filter(shipment -> !shipment.terminal() || shipment.reception().isPresent())
                    .forEach(shipment -> owners.add(shipment.authorization().claimantId()));
            return Set.copyOf(owners);
        });
        var contracts = trade.contracts().values().stream().filter(GoodsTradeContract::terminal)
                .filter(contract -> !physical.contains(contract.id()) && !shipmentOwners.contains(contract.id()))
                .sorted(Comparator.comparing(GoodsTradeContract::id)).limit(MAX_PER_REVIEW)
                .map(GoodsTradeContract::id).collect(java.util.stream.Collectors.toSet());
        var referenced = new HashSet<SubjectId>(); trade.contracts().values().stream()
                .filter(contract -> !contracts.contains(contract.id())).forEach(contract -> {
                    referenced.add(contract.sellOrderId()); referenced.add(contract.buyOrderId());
                });
        var orders = trade.orders().values().stream()
                .filter(order -> (order.expiresAtTick() < now || order.availableQuantity() == 0)
                        && !referenced.contains(order.id()) && !physical.contains(order.id()))
                .sorted(Comparator.comparing(GoodsTradeOrder::id)).limit(MAX_PER_REVIEW)
                .map(GoodsTradeOrder::id).collect(java.util.stream.Collectors.toSet());
        return orders.isEmpty() && contracts.isEmpty() ? Optional.empty() : Optional.of(new GoodsTradeRetired(orders, contracts));
    }
}
