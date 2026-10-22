package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Closed composition root. A producer supplies the client key; no recipe or ID-based dispatch. */
public final class ProductionOperationCompletionComposition {
    private static final Map<ProductionOrderKind, ProductionOrderPort> ORDERS = register(List.of(new BakeryStationCompletion()));
    private static Map<ProductionOrderKind, ProductionOrderPort> register(List<ProductionOrderPort> entries) {
        var ports = new EnumMap<ProductionOrderKind, ProductionOrderPort>(ProductionOrderKind.class);
        for (var entry : entries) if (ports.putIfAbsent(Objects.requireNonNull(entry.kind()), entry) != null)
            throw new IllegalArgumentException("duplicate production order capability");
        if (!ports.keySet().equals(Set.of(ProductionOrderKind.values())))
            throw new IllegalArgumentException("missing production order capability");
        return Map.copyOf(ports);
    }
    private static ProductionOrderPort port(FrontierWorldState state, ProductionOperation operation, ProductionOperation.OrderInitiator order) {
        var port = ORDERS.get(order.kind());
        if (port == null || !order.orderId().equals(operation.orderId()))
            throw new IllegalArgumentException("station client lacks its exact registered owner declaration");
        port.validate(state, operation);
        return port;
    }
    private ProductionOperationCompletionComposition() { }
    /** Inverse reference closure consumes declared owner projections, never discovers a client from membership. */
    static void validateRelations(ProductionStations stations, Map<ProductionOrderKind, Set<io.farfrontier.palemirror.frontier.v3.api.SubjectId>> activeOrders) {
        if (!activeOrders.keySet().equals(ORDERS.keySet())) throw new IllegalArgumentException("station order relation projection lacks its registered owner");
        for (var machine : stations.machines().values()) machine.operation().ifPresent(operation -> {
            if (operation.initiator() instanceof ProductionOperation.OrderInitiator order
                    && !activeOrders.get(order.kind()).contains(order.orderId()))
                throw new IllegalArgumentException("machine operation has no live declared client relationship");
        });
    }
    public static FrontierWorldStateUpdate completed(FrontierWorldState before, ProductionOperation operation) {
        return switch (operation.initiator()) {
            case ProductionOperation.OrderInitiator order -> port(before, operation, order).completed(before, operation);
            case ProductionOperation.PlayerInitiator ignored -> FrontierWorldStateUpdate.begin();
            case ProductionOperation.SettlementInitiator ignored -> FrontierWorldStateUpdate.begin();
        };
    }
    static FungibleForfeitureSettlement inputLost(FrontierWorldState before, ProductionOperation operation, FungibleForfeitureSettlement transaction) {
        return switch (operation.initiator()) {
            case ProductionOperation.OrderInitiator order -> port(before, operation, order).inputLost(before, operation, transaction);
            case ProductionOperation.PlayerInitiator ignored -> transaction;
            case ProductionOperation.SettlementInitiator ignored -> transaction;
        };
    }
    static java.util.List<SourceStockClaimLossOwner.ReleasedActivity> released(FrontierWorldState before, ProductionOperation operation) {
        return switch (operation.initiator()) {
            case ProductionOperation.OrderInitiator order -> port(before, operation, order).released(before, operation);
            case ProductionOperation.PlayerInitiator ignored -> java.util.List.of();
            case ProductionOperation.SettlementInitiator ignored -> java.util.List.of();
        };
    }
}
