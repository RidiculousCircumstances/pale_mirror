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
    private record PartyInputs(GoodsParticipant participant, SettlementEconomicPlanningState economy) {
        boolean matches(FrontierWorldState state) {
            var participants = state.companies().goodsTrade().participants();
            var current = participants.participants().get(participant.party().id());
            return current != null && current.party().equals(participant.party())
                    && current.policy().equals(participant.policy()) && current.settlementId().equals(participant.settlementId())
                    && current.known().equals(participant.known()) && current.prices().equals(participant.prices())
                    && participants.economicState(participant.party().id()).sameNonMarketInputs(economy);
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
    private final Map<SubjectId, GoodsTradeOrder> offers;
    private final List<Peer> sellers;
    private record Capacity(boolean orders, boolean groups, boolean missions, boolean shipments) {
        static Capacity capture(FrontierWorldState state) {
            return new Capacity(state.companies().goodsTrade().orders().size() < GoodsTradeState.MAX_ORDERS,
                    state.unitGroups().groups().size() < io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState.MAX_GROUPS,
                    state.shipments().missions().size() < io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState.MAX_GROUPS,
                    state.shipments().shipments().size() < ShipmentState.MAX_SHIPMENTS);
        }
    }
    private GoodsOpportunityInputs(PartyInputs buyer, Map<String, FixedScalar> limits, Map<SubjectId, GoodsTradeOrder> offers,
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
    boolean matches(FrontierWorldState state, long now) {
        return capacity.equals(Capacity.capture(state)) && buyer.matches(state) && sellers.stream().allMatch(seller -> seller.matches(state))
                && offers.equals(GoodsMarketInterest.purchaseOffers(state.companies().goodsTrade(),
                        state.companies().goodsTrade().participants().participants().get(buyer.participant().party().id()), limits, now));
    }
}
