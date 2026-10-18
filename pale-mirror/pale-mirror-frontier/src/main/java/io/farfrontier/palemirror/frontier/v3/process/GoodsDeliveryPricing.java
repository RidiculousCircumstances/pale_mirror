package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionProvisioning;
import io.farfrontier.palemirror.frontier.v3.model.market.DeliveryCost;
import java.util.*;

/** Composition of declared journey, appetite and market prices. Neither a
 * navigator nor an inventory/finance owner; unadmitted quotes cannot move goods. */
final class GoodsDeliveryPricing {
    record Reserved(FrontierWorldState state, List<ProposedEvent> events, List<GoodsTripAssembly.Parcel> parcels) { }
    static DeliveryCost estimate(FrontierWorldState state, GoodsParticipant organizer, long duration, int workers) {
        var life = state.bootstrap().ruleset().residentLife();
        var food = life.foods().foods().values().stream().min(Comparator.comparing(FoodCatalog.Food::itemKind)).orElseThrow();
        int items = ExpeditionProvisioning.requiredFoodItems(duration, life.mealTargetUnits(), life.metabolismDefaultPermille(), food, life);
        return new DeliveryCost(foodPrice(state, organizer, food.itemKind()).multiply(Math.multiplyExact(items, workers)),
                Math.multiplyExact(duration, workers));
    }
    static DeliveryCost exact(FrontierWorldState state, GoodsParticipant organizer, TransportMissionStarted declaration, long now) {
        var load = declaration.mission().supplies().orElseThrow();
        var life = state.bootstrap().ruleset().residentLife();
        var food = life.foods().foods().get(load.foodKind());
        int items = 0;
        for (var actor : load.foodTargets().keySet()) {
            var resident = Objects.requireNonNull(state.humanPopulation().residents().get(actor), "declared provisioned person");
            items = Math.addExact(items, ExpeditionProvisioning.requiredFoodItems(load.forecastDurationTicks(),
                    life.mealTargetUnits(), resident.characteristics().effectiveMetabolismPermille(now), food, life));
        }
        return new DeliveryCost(foodPrice(state, organizer, load.foodKind()).multiply(items),
                Math.multiplyExact(load.forecastDurationTicks(), load.foodTargets().size()));
    }
    private static FixedScalar foodPrice(FrontierWorldState state, GoodsParticipant participant, String item) {
        var rules = state.bootstrap().ruleset().goodsTrade();
        var commodity = rules.policies().get(participant.policy()).stream().filter(c -> c.itemKind().equals(item)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("travel food lacks a declared market valuation"));
        return state.companies().goodsTrade().priceHistory().reference(item, rules.pricing().recencyTicks())
                .orElse(commodity.minimumSellPrice());
    }
    /** Rebuild only the uncommitted draft with complete delivered prices. The
     * same reservation then covers partial acceptance, loss and cancellation. */
    static Optional<Reserved> reserve(FrontierWorldState initial, List<ProposedEvent> reservations,
                                      List<GoodsTripAssembly.Parcel> parcels, DeliveryCost cost, long now) {
        var total = new FixedScalar(parcels.stream().mapToLong(p -> p.contract().goodsUnitPrice().multiply(p.claim().quantity()).raw())
                .reduce(0, Math::addExact));
        var events = new ArrayList<ProposedEvent>(); var priced = new ArrayList<GoodsTripAssembly.Parcel>();
        var state = initial;
        for (int i = 0; i < parcels.size(); i++) {
            var parcel = parcels.get(i); var original = parcel.contract();
            var unitCharge = original.fulfillment().mode() == GoodsTradeFulfillment.Mode.SELLER_DELIVERY
                    ? cost.unitCharge(original.goodsUnitPrice(), total) : FixedScalar.ZERO;
            var contract = original.quoteDelivery(unitCharge);
            var buyer = state.companies().goodsTrade().participants().participants().get(contract.buyer().id());
            var bid = GoodsOrderMatching.intent(state, buyer, contract.itemKind(), GoodsTradeOrder.Side.BUY);
            if (bid.isEmpty() || contract.deliveredUnitPrice().compareTo(bid.orElseThrow().limit()) > 0
                    || contract.deliveredUnitPrice().compareTo(state.companies().goodsTrade().orders().get(contract.buyOrderId()).unitPriceLimit()) > 0
                    || GoodsParticipantFunding.purchase(GoodsParticipantView.read(state, buyer), state.bootstrap().ruleset().goodsTrade(),
                            contract.itemKind()).compareTo(contract.outstandingPrice()) < 0) return Optional.empty();
            var originalEvent = (GoodsTradeReserved) reservations.get(i).payload();
            var event = new GoodsTradeReserved(contract, originalEvent.allocations());
            state = GoodsTradeStateSupport.reserve(state, contract.seller().id(), contract, event.allocations(), now);
            events.add(new ProposedEvent(contract.seller().id(), event));
            priced.add(new GoodsTripAssembly.Parcel(contract, parcel.claim(), parcel.sourceAccount()));
        }
        return Optional.of(new Reserved(state, List.copyOf(events), List.copyOf(priced)));
    }
    private GoodsDeliveryPricing() { }
}
