package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Bounded negative-choice proof owned by trade, not the generic continuation.
 * A market notification alone changes no purchasing condition. New relevant
 * offers (including a previously absent commodity) or hard inputs reopen it. */
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
    private final PartyInputs buyer;
    private final Map<String, FixedScalar> limits;
    private final Capacity capacity;
    private final Map<SubjectId, Boolean> workforce;
    private final Map<SubjectId, GoodsTradeOrder> offers;
    private final List<PartyInputs> sellers;
    private record Capacity(boolean orders, boolean groups, boolean missions, boolean shipments) {
        static Capacity capture(FrontierWorldState state) {
            return new Capacity(state.companies().goodsTrade().orders().size() < GoodsTradeState.MAX_ORDERS,
                    state.unitGroups().groups().size() < io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState.MAX_GROUPS,
                    state.shipments().missions().size() < io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState.MAX_GROUPS,
                    state.shipments().shipments().size() < ShipmentState.MAX_SHIPMENTS);
        }
    }
    private GoodsOpportunityInputs(PartyInputs buyer, Map<String, FixedScalar> limits, Map<SubjectId, GoodsTradeOrder> offers,
            List<PartyInputs> sellers, Capacity capacity, Map<SubjectId, Boolean> workforce) {
        this.buyer = buyer; this.limits = Map.copyOf(limits); this.offers = offers; this.sellers = List.copyOf(sellers);
        this.capacity = capacity; this.workforce = Map.copyOf(workforce);
    }
    static GoodsOpportunityInputs capture(FrontierWorldState state, GoodsParticipant buyer, Map<String, FixedScalar> limits, long now) {
        var participants = state.companies().goodsTrade().participants();
        var offers = GoodsMarketInterest.purchaseOffers(state.companies().goodsTrade(), buyer, limits, now);
        var sellers = offers.values().stream().map(order -> order.party().id()).distinct().sorted()
                .map(id -> new PartyInputs(participants.participants().get(id), participants.economicState(id))).toList();
        var workforce = new HashMap<SubjectId, Boolean>();
        if (!offers.isEmpty()) {
            captureWorkforce(state, buyer.settlementId(), workforce);
            sellers.forEach(seller -> captureWorkforce(state, seller.participant().settlementId(), workforce));
        }
        return new GoodsOpportunityInputs(new PartyInputs(buyer, participants.economicState(buyer.party().id())),
                limits, offers, sellers, Capacity.capture(state), workforce);
    }
    private static void captureWorkforce(FrontierWorldState state, SubjectId home, Map<SubjectId, Boolean> workforce) {
        for (var resident : state.humanPopulation().residentsIn(home))
            workforce.put(resident.id(), ActorExecutionCoordinator.ordinaryWorkAdmission(state, resident.id()).permitted());
    }
    boolean matches(FrontierWorldState state, long now) {
        return capacity.equals(Capacity.capture(state)) && buyer.matches(state) && sellers.stream().allMatch(seller -> seller.matches(state))
                && workforce.entrySet().stream().allMatch(entry -> entry.getValue()
                    == ActorExecutionCoordinator.ordinaryWorkAdmission(state, entry.getKey()).permitted())
                && offers.equals(GoodsMarketInterest.purchaseOffers(state.companies().goodsTrade(),
                        state.companies().goodsTrade().participants().participants().get(buyer.participant().party().id()), limits, now));
    }
}
