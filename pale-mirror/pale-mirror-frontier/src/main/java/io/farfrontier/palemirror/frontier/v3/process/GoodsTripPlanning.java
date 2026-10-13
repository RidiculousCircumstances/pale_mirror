package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection;
import java.util.*;

/** One transaction publishes sales only after exact logistics admission has succeeded on an immutable draft. */
final class GoodsTripPlanning {
    record Result(FrontierWorldState state, List<ProposedEvent> events, String decision) { }
    private record Candidate(Result result, ProcurementSelection.Offer offer) { }
    static Result plan(FrontierWorldState initial, GoodsParticipant reviewer, long now) {
        if (initial.unitGroups().groups().size() >= io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState.MAX_GROUPS
                || initial.shipments().missions().size() >= io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState.MAX_GROUPS
                || initial.shipments().shipments().size() >= ShipmentState.MAX_SHIPMENTS)
            return new Result(initial, List.of(), "WAITING_FOR_TRANSPORT_RETENTION; reserved=0");
        String decision = "NO_MUTUAL_KNOWN_QUOTE";
        var candidates = new ArrayList<Candidate>();
        var buyer = reviewer;
        var demand = GoodsParticipantView.read(initial, buyer);
        var requirements = GoodsParticipantPolicies.require(buyer).decide(demand, initial.bootstrap().ruleset().goodsTrade())
                .stream().filter(intent -> intent.side() == GoodsTradeOrder.Side.BUY)
                .map(intent -> new ProcurementSelection.Requirement(intent.itemKind(), intent.quantity(),
                        demand.supplyPlan().map(plan -> plan.require(intent.itemKind()).deadlineTicks()).orElse(Long.MAX_VALUE))).toList();
        if (requirements.isEmpty()) return new Result(initial, List.of(), "NO_CURRENT_PURCHASE_REQUIREMENT");
        for (var peer : reviewer.known()) {
            var other = initial.companies().goodsTrade().participants().participants().get(peer.party().id());
            if (other == null || !other.party().equals(peer.party()) || !other.endpoint().equals(peer.endpoint())) continue;
            for (var mode : GoodsTradeFulfillment.Mode.values()) {
                var seller = other;
                var state = initial; var reservations = new ArrayList<ProposedEvent>(); var parcels = new ArrayList<GoodsTripAssembly.Parcel>();
                int reviews = 0; var considered = new HashSet<String>();
                int cargoLimit = Math.min(initial.bootstrap().ruleset().goodsTrade().supplyPlanning().maximumTripCargoStacks(),
                        ShipmentState.MAX_SHIPMENTS - initial.shipments().shipments().size());
                while (parcels.size() < cargoLimit && state.companies().goodsTrade().contracts().size() < GoodsTradeState.MAX_CONTRACTS
                        && reviews < initial.bootstrap().ruleset().goodsTrade().maximumPairReviews()) {
                    var search = GoodsOrderMatching.first(state, reviewer, now, Optional.of(seller.party()), Optional.of(buyer.party()), considered);
                    reviews += search.checkedPairs();
                    if (search.match().isEmpty()) {
                        if (!considered.isEmpty()) { considered.clear(); continue; }
                        if (parcels.isEmpty()) decision = search.disposition(); break;
                    }
                    var raw = search.match().orElseThrow();
                    var fulfillment = new GoodsTradeFulfillment(mode, mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? seller.party() : buyer.party());
                    var matchContract = raw.contract().declareFulfillment(fulfillment);
                    if (raw.allocations().size() != 1) throw new IllegalArgumentException("trip selection requires one exact allocation per match");
                    considered.add(matchContract.itemKind());
                    if (matchContract.sourceContainerId().equals(matchContract.receiverContainerId())) break;
                    state = GoodsTradeStateSupport.reserve(state, seller.party().id(), matchContract, raw.allocations(), now);
                    reservations.add(new ProposedEvent(seller.party().id(), new GoodsTradeReserved(matchContract, raw.allocations())));
                    for (var allocation : raw.allocations()) parcels.add(new GoodsTripAssembly.Parcel(matchContract, allocation.claim(), allocation.accountId()));
                }
                if (parcels.isEmpty()) continue;
                // Bounded capacity backtracking, never cancel/re-reserve a published sale.
                // A missing animal must not forbid a smaller feasible walking shipment.
                for (int count = parcels.size(); count > 0; count--) {
                    var selected = List.copyOf(parcels.subList(0, count));
                    var cause = dispatchCause(initial, buyer, selected, now);
                    if (cause == TripDispatchPolicy.Cause.ACCUMULATING) { decision = "ACCUMULATING_PREFERRED_LOAD"; continue; }
                    var selectedState = initial; var selectedEvents = new ArrayList<ProposedEvent>();
                    for (int index = 0; index < count; index++) {
                        var event = reservations.get(index); var reservation = (GoodsTradeReserved) event.payload();
                        selectedState = GoodsTradeStateSupport.reserve(selectedState, seller.party().id(), reservation.contract(), reservation.allocations(), now);
                        selectedEvents.add(event);
                    }
                    Optional<TransportMissionStarted> trip;
                    try { trip = GoodsTripAssembly.prepare(selectedState, seller, buyer, selected, mode, now); }
                    catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
                        // Calculation dependencies have already been declared by the shared planner.
                        // Nothing in this draft has been published: its ordinary continuation owns the retry.
                        decision = "WAITING_FOR_ROUTE; status=" + unavailable.status() + "; reserved=0";
                        continue;
                    }
                    if (trip.isEmpty()) { decision = "WAITING_FOR_AVAILABLE_TRANSPORT_OR_PROVISIONS; reserved=0"; continue; }
                    var declaration = trip.orElseThrow();
                    selectedEvents.addAll(GoodsTripAssembly.events(declaration, now));
                    var admitted = TransportMissionProcess.admit(selectedState, declaration.mission().id(), declaration, now);
                    var result = new Result(admitted, List.copyOf(selectedEvents), "TRIP_ADMITTED; cause=" + cause + "; parcels=" + count
                            + "; items=" + selected.stream().mapToInt(p -> p.claim().quantity()).sum()
                            + "; members=" + declaration.group().members().size() + "; mission=" + declaration.mission().id().value());
                    candidates.add(new Candidate(result, offer(declaration, selected)));
                    break;
                }
        }
            }
        var selected = new ProcurementSelection().select(requirements, candidates.stream().map(Candidate::offer).toList());
        if (selected.isPresent()) return candidates.stream().filter(candidate -> candidate.offer().id().equals(selected.orElseThrow()))
                .findFirst().orElseThrow().result();
        return new Result(initial, List.of(), decision);
    }
    private static ProcurementSelection.Offer offer(TransportMissionStarted declaration, List<GoodsTripAssembly.Parcel> parcels) {
        var quantities = new TreeMap<String, Integer>(); var amounts = new TreeMap<String, Long>();
        for (var parcel : parcels) {
            var contract = parcel.contract();
            quantities.merge(contract.itemKind(), parcel.claim().quantity(), Math::addExact);
            amounts.merge(contract.itemKind(), contract.deliveredUnitPrice().multiply(parcel.claim().quantity()).raw(), Math::addExact);
        }
        var lines = new TreeMap<String, ProcurementSelection.Line>();
        quantities.forEach((item, quantity) -> lines.put(item, new ProcurementSelection.Line(quantity,
                new FixedScalar(Math.ceilDiv(amounts.get(item), quantity)))));
        // Quotes retain their agreed price; no second cash payment or internal wage
        // is invented. The bounded full-itinerary forecast conservatively bounds
        // reception. Supply deadlines and arrival forecasts are remaining durations,
        // not absolute simulation instants; no world age enters their comparison.
        return new ProcurementSelection.Offer(declaration.mission().id(), lines, FixedScalar.ZERO,
                declaration.mission().supplies().orElseThrow().forecastDurationTicks(),
                declaration.mission().organization().itinerary() == TransportOrganization.Itinerary.DESTINATION_COLLECTION
                    ? Math.multiplyExact(declaration.group().members().size(), declaration.mission().supplies().orElseThrow().forecastDurationTicks()) : 0);
    }
    static TripDispatchPolicy.Cause dispatchCause(FrontierWorldState state, GoodsParticipant buyer, List<GoodsTripAssembly.Parcel> parcels, long now) {
        var rules = state.bootstrap().ruleset().goodsTrade(); var view = GoodsParticipantView.read(state, buyer);
        var quantities = new TreeMap<String, Integer>(); var age = new HashMap<String, Long>();
        for (var parcel : parcels) {
            var contract = parcel.contract(); quantities.merge(contract.itemKind(), parcel.claim().quantity(), Math::addExact);
            var order = state.companies().goodsTrade().orders().get(contract.buyOrderId());
            long wait = Math.max(0, now - Math.max(0, order.expiresAtTick() - rules.orderLifetime()));
            age.merge(contract.itemKind(), wait, Math::max);
        }
        return TripDispatchPolicy.assess(quantities.entrySet().stream().map(e -> new TripDispatchPolicy.Goods(e.getValue(), age.get(e.getKey()),
                rules.supplyPlanning().require(e.getKey()), view.supplyPlan().map(p -> p.require(e.getKey()))
                    .orElseGet(() -> new SupplyPlanner.Demand(0, 0, 0, Long.MAX_VALUE, Set.of(), Optional.empty())))).toList());
    }
    private GoodsTripPlanning() { }
}
