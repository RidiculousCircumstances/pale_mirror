package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Bounded negative-choice proof owned by trade, not the generic continuation.
 * A market notification alone changes no purchasing condition. New relevant
 * offers (including a previously absent commodity) or buyer hard inputs reopen it.
 * Peer stock, prices and crew remain dated observations until the next review;
 * only the current exact admission can promise their resources or workforce. */
final class GoodsOpportunityInputs {
    enum Change { RETENTION, BUYER_DECLARATION, BUYER_PRICES, BUYER_PLAN, BUYER_INPUT, PEER_DECLARATION, OFFERS }
    private record PartyInputs(GoodsParticipant participant, SettlementEconomicPlanningState economy) {
        Optional<Change> change(FrontierWorldState state) {
            var participants = state.companies().goodsTrade().participants();
            var current = participants.participants().get(participant.party().id());
            if (current == null || !current.party().equals(participant.party())
                    || current.policy() != participant.policy() || !current.settlementId().equals(participant.settlementId())
                    || !current.known().equals(participant.known())) return Optional.of(Change.BUYER_DECLARATION);
            if (!current.prices().equals(participant.prices())) return Optional.of(Change.BUYER_PRICES);
            var inputs = participants.economicState(participant.party().id());
            if (!inputs.accepted().equals(economy.accepted())) return Optional.of(Change.BUYER_PLAN);
            return inputs.sameNonMarketInputs(economy) ? Optional.empty() : Optional.of(Change.BUYER_INPUT);
        }
    }
    private record Peer(GoodsTradeParty party, GoodsPolicyKind policy, SubjectId home) {
        boolean matches(FrontierWorldState state) {
            var current = state.companies().goodsTrade().participants().participants().get(party.id());
            return current != null && current.party().equals(party) && current.policy().equals(policy)
                    && current.settlementId().equals(home);
        }
    }
    private final PartyInputs buyer;
    private final Map<String, FixedScalar> limits;
    private final Capacity capacity;
    private final Map<SubjectId, GoodsMarketInterest.PurchaseOffer> offers;
    private final List<Peer> sellers;
    private record Capacity(boolean orders, boolean groups, boolean missions, boolean shipments) {
        static Capacity capture(FrontierWorldState state) {
            return new Capacity(state.companies().goodsTrade().orders().size() < GoodsTradeState.MAX_ORDERS,
                    state.unitGroups().groups().size() < io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState.MAX_GROUPS,
                    state.shipments().missions().size() < io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState.MAX_GROUPS,
                    state.shipments().shipments().size() < ShipmentState.MAX_SHIPMENTS);
        }
    }
    private GoodsOpportunityInputs(PartyInputs buyer, Map<String, FixedScalar> limits, Map<SubjectId, GoodsMarketInterest.PurchaseOffer> offers,
            List<Peer> sellers, Capacity capacity) {
        this.buyer = buyer; this.limits = Map.copyOf(limits); this.offers = offers; this.sellers = List.copyOf(sellers);
        this.capacity = capacity;
    }
    static GoodsOpportunityInputs capture(FrontierWorldState state, GoodsParticipant buyer, Map<String, FixedScalar> limits, long now) {
        var participants = state.companies().goodsTrade().participants();
        var offers = GoodsMarketInterest.purchaseOffers(state.companies().goodsTrade(), buyer, limits, now);
        var sellers = offers.values().stream().map(order -> order.party().id()).distinct().sorted()
                .map(participants.participants()::get).map(seller -> new Peer(seller.party(), seller.policy(), seller.settlementId())).toList();
        return new GoodsOpportunityInputs(new PartyInputs(buyer, participants.economicState(buyer.party().id())),
                limits, offers, sellers, Capacity.capture(state));
    }
    boolean matches(FrontierWorldState state, long now) { return change(state, now).isEmpty(); }
    Optional<Change> change(FrontierWorldState state, long now) {
        if (!capacity.equals(Capacity.capture(state))) return Optional.of(Change.RETENTION);
        var own = buyer.change(state); if (own.isPresent()) return own;
        if (!sellers.stream().allMatch(seller -> seller.matches(state))) return Optional.of(Change.PEER_DECLARATION);
        var current = GoodsMarketInterest.purchaseOffers(state.companies().goodsTrade(),
                state.companies().goodsTrade().participants().participants().get(buyer.participant().party().id()), limits, now);
        return offers.equals(current) ? Optional.empty() : Optional.of(Change.OFFERS);
    }
}
