package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.*;

/** Read-only economic projection for one participant. A policy receives no mutable world or other stocks. */
public record GoodsParticipantView(GoodsParticipant participant, int residents, FixedScalar availableMoney,
                                   Map<String, Stock> stocks) {
    public record Stock(int ownedAtEndpoint, int unclaimedAtEndpoint, int expectedIncoming, int protectedMinimum) {
        public Stock(int ownedAtEndpoint, int unclaimedAtEndpoint, int expectedIncoming) {
            this(ownedAtEndpoint, unclaimedAtEndpoint, expectedIncoming, 0);
        }
        public Stock {
            if (ownedAtEndpoint < 0 || unclaimedAtEndpoint < 0 || unclaimedAtEndpoint > ownedAtEndpoint || expectedIncoming < 0 || protectedMinimum < 0)
                throw new IllegalArgumentException("invalid participant stock view");
        }
    }
    public GoodsParticipantView {
        Objects.requireNonNull(participant); Objects.requireNonNull(availableMoney); stocks = Map.copyOf(stocks);
        if (residents < 0 || availableMoney.raw() < 0) throw new IllegalArgumentException("invalid participant economic view");
    }
    public static GoodsParticipantView read(FrontierWorldState state, GoodsParticipant participant) {
        participant.party().validate(state.inventory().economics());
        var account = FungibleResourceCustodySupport.accountAtContainer(state, participant.endpoint().containerId());
        var ledger = state.inventory().fungibleResources();
        var result = new HashMap<String, Stock>();
        for (var commodity : state.bootstrap().ruleset().goodsTrade().policies().get(participant.policy())) {
            int owned = account.map(value -> value.lotQuantities().entrySet().stream().filter(entry -> {
                var lot = ledger.lots().get(entry.getKey());
                return lot.economicOwnerId().equals(participant.party().id()) && lot.itemKind().equals(commodity.itemKind());
            }).mapToInt(Map.Entry::getValue).reduce(0, Math::addExact)).orElse(0);
            int unclaimed = account.map(value -> ledger.unclaimedQuantity(value.id(), participant.party().id(), commodity.itemKind())).orElse(0);
            int incoming = state.companies().goodsTrade().contracts().values().stream()
                    .filter(contract -> !contract.terminal() && contract.buyer().equals(participant.party())
                            && contract.itemKind().equals(commodity.itemKind()) && contract.receiverContainerId().equals(participant.endpoint().containerId()))
                    .mapToInt(GoodsTradeContract::remainingQuantity).reduce(0, Math::addExact);
            incoming = Math.addExact(incoming, InternalShipmentIncomingStock.quantity(state.shipments(), participant.party().id(),
                    participant.endpoint().containerId(), commodity.itemKind()));
            int protectedMinimum = participant.policy() == GoodsPolicyKind.PUBLIC_SETTLEMENT
                    && commodity.itemKind().equals(SettlementFoodPolicy.BREAD)
                    ? SettlementFoodPolicy.reserveRequirement(state, participant.party().id()) : 0;
            result.put(commodity.itemKind(), new Stock(owned, unclaimed, incoming, protectedMinimum));
        }
        int residents = (int) state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(participant.endpoint().settlementId())
                        && state.actorLocations().get(resident.id()).condition().status() == ActorLifeStatus.ALIVE).count();
        return new GoodsParticipantView(participant, residents, state.inventory().economics().availableToReserve(participant.party().id()), result);
    }
}
