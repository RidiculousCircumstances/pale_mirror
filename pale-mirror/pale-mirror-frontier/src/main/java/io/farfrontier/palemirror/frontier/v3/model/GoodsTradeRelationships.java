package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import static io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships.*;

/** Read-only commercial edges; the commercial/resource/financial owners retain the actual facts. */
final class GoodsTradeRelationships {
    private GoodsTradeRelationships() { }
    static void collect(FrontierWorldState state, List<Edge> edges) {
        GoodsTradeStateSupport.validate(state);
        for (GoodsTradeOrder order : state.companies().goodsTrade().orders().values()) {
            SubjectEndpoint owner = endpoint(EntityKind.GOODS_ORDER, order.id());
            add(edges, Kind.GOODS_ORDER_PARTY, owner, EntityKind.ECONOMIC_ACCOUNT, order.party().id(), Lifecycle.ACTIVE);
            add(edges, Kind.GOODS_ORDER_COUNTERPARTY, owner, EntityKind.ECONOMIC_ACCOUNT, order.counterparty().id(), Lifecycle.ACTIVE);
            add(edges, Kind.GOODS_ORDER_CONTAINER, owner, EntityKind.CONTAINER, order.containerId(), Lifecycle.ACTIVE);
        }
        for (GoodsTradeContract contract : state.companies().goodsTrade().contracts().values()) {
            SubjectEndpoint owner = endpoint(EntityKind.GOODS_CONTRACT, contract.id());
            Lifecycle life = contract.terminal() ? Lifecycle.TERMINAL_RETAINED : Lifecycle.ACTIVE;
            add(edges, Kind.GOODS_SELL_ORDER, owner, EntityKind.GOODS_ORDER, contract.sellOrderId(), life);
            add(edges, Kind.GOODS_BUY_ORDER, owner, EntityKind.GOODS_ORDER, contract.buyOrderId(), life);
            add(edges, Kind.GOODS_SELLER, owner, EntityKind.ECONOMIC_ACCOUNT, contract.seller().id(), life);
            add(edges, Kind.GOODS_BUYER, owner, EntityKind.ECONOMIC_ACCOUNT, contract.buyer().id(), life);
            add(edges, Kind.GOODS_SOURCE_CONTAINER, owner, EntityKind.CONTAINER, contract.sourceContainerId(), life);
            add(edges, Kind.GOODS_RECEIVER_CONTAINER, owner, EntityKind.CONTAINER, contract.receiverContainerId(), life);
            if (!contract.terminal()) add(edges, Kind.GOODS_RESERVATION, owner, EntityKind.FINANCIAL_RESERVATION, contract.financialReservationId(), life);
            for (SubjectId claim : contract.outstandingClaims().keySet()) add(edges, Kind.GOODS_CLAIM, owner, EntityKind.RESOURCE_CLAIM, claim, life);
        }
    }
    private static SubjectEndpoint endpoint(EntityKind kind, SubjectId id) { return new SubjectEndpoint(kind, id); }
    private static void add(List<Edge> edges, Kind kind, SubjectEndpoint owner, EntityKind targetKind, SubjectId target, Lifecycle life) {
        edges.add(declaredEdge(kind, owner, owner, endpoint(targetKind, target), life, "trade:" + owner.id().value()));
    }
}
