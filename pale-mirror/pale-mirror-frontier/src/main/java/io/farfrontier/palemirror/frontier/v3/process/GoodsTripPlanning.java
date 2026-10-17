package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection;
import java.util.*;

/** One transaction publishes sales only after exact logistics admission has succeeded on an immutable draft. */
final class GoodsTripPlanning {
    record Result(FrontierWorldState state, List<ProposedEvent> events, String decision) { }
    private record Candidate(FrontierWorldState draft, List<ProposedEvent> reservations,
            GoodsParticipant seller, List<GoodsTripAssembly.Parcel> parcels, GoodsTradeFulfillment.Mode mode,
            TripDispatchPolicy.Cause cause, ProcurementSelection.Offer offer, List<FrontierWorldState> prefixes) { }
    static Result plan(FrontierWorldState initial, GoodsParticipant reviewer, long now) {
        return ImmutablePlanningScope.within(() -> planInScope(initial, reviewer, now));
    }
    private static Result planInScope(FrontierWorldState initial, GoodsParticipant reviewer, long now) {
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
                        demand.supplyPlan().map(plan -> plan.require(intent.itemKind()).deadlineTicks()).orElse(Long.MAX_VALUE),
                        GoodsParticipantFunding.fundsRestart(demand, initial.bootstrap().ruleset().goodsTrade(), intent.itemKind()))).toList();
        if (requirements.isEmpty()) return new Result(initial, List.of(), "NO_CURRENT_PURCHASE_REQUIREMENT");
        for (var peer : reviewer.known()) {
            var other = initial.companies().goodsTrade().participants().participants().get(peer.party().id());
            if (other == null || !other.party().equals(peer.party()) || !other.settlementId().equals(peer.settlementId())) continue;
            var reviewedPairs = new EnumMap<GoodsTradeFulfillment.Mode, Integer>(GoodsTradeFulfillment.Mode.class);
            for (var endpoints : GoodsOrderMatching.endpointPairs(initial, other, buyer, now)) {
            for (var mode : GoodsTradeFulfillment.Mode.values()) {
                var seller = other;
                var state = initial; var reservations = new ArrayList<ProposedEvent>(); var parcels = new ArrayList<GoodsTripAssembly.Parcel>();
                var reservedPrefixes = new ArrayList<FrontierWorldState>(); reservedPrefixes.add(initial);
                int reviews = reviewedPairs.getOrDefault(mode, 0); var considered = new HashSet<String>();
                int cargoLimit = Math.min(initial.bootstrap().ruleset().goodsTrade().supplyPlanning().maximumTripCargoStacks(),
                        ShipmentState.MAX_SHIPMENTS - initial.shipments().shipments().size());
                while (parcels.size() < cargoLimit && state.companies().goodsTrade().contracts().size() < GoodsTradeState.MAX_CONTRACTS
                        && reviews < initial.bootstrap().ruleset().goodsTrade().maximumPairReviews()) {
                    var search = GoodsOrderMatching.first(state, reviewer, now, Optional.of(seller.party()), Optional.of(buyer.party()), considered, Optional.of(endpoints));
                    reviews += search.checkedPairs();
                    reviewedPairs.put(mode, reviews); // Additional warehouses share the original per-peer/mode work budget.
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
                    reservedPrefixes.add(state);
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
                    // Prefixes are already exact immutable reservation results for this
                    // candidate/mode. Backtracking must not replay the same drafts.
                    var selectedState = reservedPrefixes.get(count);
                    var selectedEvents = new ArrayList<>(reservations.subList(0, count));
                    long estimate;
                    try {
                        var source = GoodsParticipantStorage.endpoint(initial, selected.getFirst().contract().sourceContainerId());
                        var receiver = GoodsParticipantStorage.endpoint(initial, selected.getFirst().contract().receiverContainerId());
                        var knowledge = ShipmentEndpointComposition.knowledge(initial, List.of(source, receiver));
                        var outward = knowledge.estimateBetween(source.station(), receiver.station());
                        var returning = knowledge.estimateBetween(receiver.station(), source.station());
                        estimate = initial.bootstrap().ruleset().expedition().plannedDuration(
                                outward.estimatedEdges(), returning.estimatedEdges());
                    }
                    catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
                        decision = "WAITING_FOR_REGIONAL_ESTIMATE; status=" + unavailable.status() + "; reserved=0";
                        break; // Quantity cannot change this pair's regional connectivity.
                    }
                    var id = new SubjectId("procurement:" + WorkOpportunityIdentity.digest(seller.party().id().value()
                            + "|" + buyer.party().id().value() + "|" + mode + "|" + count
                            + "|" + endpoints.source().value() + "|" + endpoints.receiver().value()));
                    candidates.add(new Candidate(selectedState, List.copyOf(selectedEvents), seller, selected, mode, cause,
                            offer(id, selected, estimate, mode), List.copyOf(reservedPrefixes.subList(0, count + 1))));
                    break;
                }
            }
            }
        }
        while (!candidates.isEmpty()) {
            var selected = new ProcurementSelection().select(requirements, candidates.stream().map(Candidate::offer).toList(),
                    initial.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().map(SourcingRules::labourValuePerTick).orElse(0L));
            if (selected.isEmpty()) break;
            var candidate = candidates.stream().filter(c -> c.offer().id().equals(selected.orElseThrow())).findFirst().orElseThrow();
            candidates.remove(candidate);
            Optional<TransportMissionStarted> trip;
            try { trip = GoodsTripAssembly.prepare(candidate.draft(), candidate.seller(), buyer,
                    candidate.parcels(), candidate.mode(), now); }
            catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
                decision = "WAITING_FOR_SELECTED_TRIP; status=" + unavailable.status() + "; reserved=0";
                // One selected pending preparation, never full-route fan-out for all quotes.
                if (unavailable.status() == io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING)
                    return new Result(initial, List.of(), decision);
                smaller(initial, buyer, candidate, now).ifPresent(candidates::add);
                continue;
            }
            if (trip.isEmpty()) {
                decision = "WAITING_FOR_AVAILABLE_TRANSPORT_OR_PROVISIONS; reserved=0";
                smaller(initial, buyer, candidate, now).ifPresent(candidates::add);
                continue;
            }
            var declaration = trip.orElseThrow(); var events = new ArrayList<>(candidate.reservations());
            events.addAll(GoodsTripAssembly.events(declaration, now));
            var admitted = TransportMissionProcess.admit(candidate.draft(), declaration.mission().id(), declaration, now);
            return new Result(admitted, List.copyOf(events), "TRIP_ADMITTED; cause=" + candidate.cause()
                    + "; parcels=" + candidate.parcels().size() + "; items=" + candidate.parcels().stream().mapToInt(p -> p.claim().quantity()).sum()
                    + "; members=" + declaration.group().members().size() + "; mission=" + declaration.mission().id().value());
        }
        return new Result(initial, List.of(), decision);
    }
    private static Optional<Candidate> smaller(FrontierWorldState initial, GoodsParticipant buyer, Candidate previous, long now) {
        for (int count = previous.parcels().size() - 1; count > 0; count--) {
            var parcels = List.copyOf(previous.parcels().subList(0, count));
            var cause = dispatchCause(initial, buyer, parcels, now);
            if (cause == TripDispatchPolicy.Cause.ACCUMULATING) continue;
            return Optional.of(new Candidate(previous.prefixes().get(count), List.copyOf(previous.reservations().subList(0, count)),
                    previous.seller(), parcels, previous.mode(), cause,
                    offer(previous.offer().id(), parcels, previous.offer().arrivalDelayTicks(), previous.mode()), previous.prefixes()));
        }
        return Optional.empty();
    }
    private static ProcurementSelection.Offer offer(SubjectId id, List<GoodsTripAssembly.Parcel> parcels,
            long estimatedDuration, GoodsTradeFulfillment.Mode mode) {
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
        // is invented. This approximate regional duration ranks alternatives only;
        // selected-trip preparation separately proves exact provisions and geometry.
        return new ProcurementSelection.Offer(id, lines, FixedScalar.ZERO, estimatedDuration,
                mode == GoodsTradeFulfillment.Mode.BUYER_COLLECTION
                    ? Math.multiplyExact(parcels.size(), estimatedDuration) : 0);
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
