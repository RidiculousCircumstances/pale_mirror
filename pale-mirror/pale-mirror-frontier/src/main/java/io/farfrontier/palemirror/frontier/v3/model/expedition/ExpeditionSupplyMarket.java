package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Commercial food candidates at one explicitly visited trading party; no stock or route authority. */
final class ExpeditionSupplyMarket {
    private ExpeditionSupplyMarket() { }
    static ShipmentEndpoint supplier(TransportMission mission) {
        return switch (mission.organization().itinerary()) {
            case SOURCE_DELIVERY -> mission.receiver();
            case DESTINATION_COLLECTION -> mission.sender();
        };
    }
    static List<ShipmentEndpoint> storages(FrontierWorldState state, TransportMission mission, String food) {
        var supplier = supplier(mission);
        var participant = state.companies().goodsTrade().participants().participants().get(supplier.settlementId());
        if (participant == null) return List.of();
        return GoodsParticipantStorage.containers(state, participant, food).stream().sorted()
                .filter(id -> !ReferenceContainerCustody.blocksCanonicalUse(state, id))
                .filter(id -> GoodsParticipantStorage.unclaimed(state, participant, id, food) > 0)
                .map(id -> GoodsParticipantStorage.endpoint(state, id)).toList();
    }
    static Optional<GoodsSpotPurchaseAuthority.Quote> quote(FrontierWorldState state, TransportMission mission,
            ShipmentEndpoint source, String food, long now) {
        if (!source.settlementId().equals(supplier(mission).settlementId())) return Optional.empty();
        var buyer = state.companies().goodsTrade().participants().participants().get(mission.organization().sponsor().id());
        if (buyer == null) return Optional.empty();
        var current = state.bootstrap().ruleset().goodsTrade().policies().get(buyer.policy()).stream()
                .filter(c -> c.itemKind().equals(food)).map(c -> GoodsParticipantPricing.bid(buyer,c)).max(Comparator.naturalOrder());
        var retained = mission.supplies().flatMap(ExpeditionSupplyLoad::purchase)
                .filter(p -> p.foodKind().equals(food) && p.supplier().settlementId().equals(source.settlementId())).map(ExpeditionSupplyPurchase::unitPrice);
        var limit = java.util.stream.Stream.concat(current.stream(),retained.stream()).max(Comparator.naturalOrder());
        return limit.flatMap(price -> GoodsSpotPurchaseAuthority.quote(state,source.containerId(),source.settlementId(),
                mission.organization().sponsor().id(),food,price,now));
    }
}
