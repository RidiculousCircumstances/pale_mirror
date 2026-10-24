package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionTiming;
import java.util.*;

/** One transaction publishes sales only after exact logistics admission has succeeded on an immutable draft. */
final class GoodsTripPlanning {
    record Result(FrontierWorldState state, List<ProposedEvent> events, String decision) { }
    private record Candidate(FrontierWorldState draft, List<ProposedEvent> reservations,
            GoodsParticipant seller, List<GoodsTripAssembly.Parcel> parcels, GoodsTradeFulfillment.Mode mode,
            ProcurementSelection.Offer offer, List<FrontierWorldState> prefixes, ExpeditionTiming timing) { }
    static Result plan(FrontierWorldState initial, GoodsParticipant reviewer, long now) {
        return ImmutablePlanningScope.within(() -> prepare(initial, reviewer, now).get());
    }
    /** Snapshot-only alternatives; only the selected candidate requests navigation evidence. */
    static java.util.function.Supplier<Result> prepare(FrontierWorldState initial, GoodsParticipant reviewer, long now) {
        var retirement = ShipmentRetention.collect(initial, ShipmentRetention.MAX_RETIREMENTS, now);
        var completion = prepareInScope(retirement.state(), reviewer, now);
        return () -> {
            var planned = completion.get();
            var events = new ArrayList<>(retirement.events()); events.addAll(planned.events());
            return new Result(planned.state(), List.copyOf(events), planned.decision());
        };
    }
    private static java.util.function.Supplier<Result> prepareInScope(FrontierWorldState initial, GoodsParticipant reviewer, long now) {
        if (initial.unitGroups().groups().size() >= io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState.MAX_GROUPS
                || initial.shipments().missions().size() >= io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState.MAX_GROUPS
                || initial.shipments().shipments().size() >= ShipmentState.MAX_SHIPMENTS)
            return () -> new Result(initial, List.of(), "WAITING_FOR_TRANSPORT_RETENTION; reserved=0");
        String decision = "NO_MUTUAL_KNOWN_QUOTE";
        var candidates = new ArrayList<Candidate>();
        var buyer = reviewer;
        var demand = GoodsParticipantAssessment.trading(initial, buyer, now);
        var requirements = GoodsParticipantPolicies.require(buyer).decide(demand, initial.bootstrap().ruleset().goodsTrade())
                .stream().filter(intent -> intent.side() == GoodsTradeOrder.Side.BUY)
                .map(intent -> new ProcurementSelection.Requirement(intent.itemKind(), intent.quantity(),
                        demand.supplyPlan().map(plan -> plan.require(intent.itemKind()).deadlineTicks()).orElse(Long.MAX_VALUE),
                        GoodsParticipantFunding.fundsRestart(demand, initial.bootstrap().ruleset().goodsTrade(), intent.itemKind()))).toList();
        if (requirements.isEmpty()) return () -> new Result(initial, List.of(), "NO_CURRENT_PURCHASE_REQUIREMENT");
        for (var peer : reviewer.known()) {
            var other = initial.companies().goodsTrade().participants().participants().get(peer.party().id());
            if (other == null || !other.party().equals(peer.party()) || !other.settlementId().equals(peer.settlementId())) continue;
            var reviewedPairs = new EnumMap<GoodsTradeFulfillment.Mode, Integer>(GoodsTradeFulfillment.Mode.class);
            for (var endpoints : GoodsOrderMatching.endpointPairs(initial, other, buyer, now)) {
            // Connectivity is relevant only after current consent, stock,
            // receiving capacity and funds permit at least a goods-only match.
            // This is a necessary condition, not admission: freight and every
            // exact allocation are rechecked on the candidate draft below.
            var feasible = GoodsOrderMatching.first(initial, reviewer, now,
                    Optional.of(other.party()), Optional.of(buyer.party()), Set.of(), Optional.of(endpoints));
            if (feasible.match().isEmpty()) { decision = feasible.disposition(); continue; }
            for (var mode : GoodsTradeFulfillment.Mode.values()) {
                var forecast = ProcurementTravelForecast.between(initial, endpoints.source(), endpoints.receiver(), itinerary(mode));
                var timing = new ExpeditionTiming(forecast.deliveryTicks(), forecast.journeyTicks());
                long estimate = timing.durationTicks();
                var seller = other;
                var state = initial; var reservations = new ArrayList<ProposedEvent>(); var parcels = new ArrayList<GoodsTripAssembly.Parcel>();
                var reservedPrefixes = new ArrayList<FrontierWorldState>(); reservedPrefixes.add(initial);
                int reviews = reviewedPairs.getOrDefault(mode, 0); var considered = new HashSet<String>();
                int cargoLimit = Math.min(initial.bootstrap().ruleset().goodsTrade().supplyPlanning().maximumTripCargoStacks(),
                        ShipmentState.MAX_SHIPMENTS - initial.shipments().shipments().size());
                while (parcels.size() < cargoLimit && state.companies().goodsTrade().contracts().size() < GoodsTradeState.MAX_CONTRACTS
                        && reviews < initial.bootstrap().ruleset().goodsTrade().maximumPairReviews()) {
                    var expense = mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY
                            ? GoodsTransportOptions.forecast(initial, seller, estimate, parcels.size() + 1, now).orElseThrow().cost().resourceExpense() : FixedScalar.ZERO;
                    var search = GoodsOrderMatching.first(state, reviewer, now, Optional.of(seller.party()), Optional.of(buyer.party()),
                            considered, Optional.of(endpoints), expense);
                    reviews += search.checkedPairs();
                    reviewedPairs.put(mode, reviews); // Additional warehouses share the original per-peer/mode work budget.
                    if (search.match().isEmpty()) {
                        if (!considered.isEmpty()) { considered.clear(); continue; }
                        if (parcels.isEmpty()) decision = search.disposition(); break;
                    }
                    var raw = search.match().orElseThrow();
                    var fulfillment = new GoodsTradeFulfillment(mode, mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? seller.party() : buyer.party());
                    var matchContract = raw.contract().declareFulfillment(fulfillment);
                    if (raw.allocations().size() + matchContract.individualAllocations().size() != 1) throw new IllegalArgumentException("trip selection requires one exact allocation per match");
                    considered.add(matchContract.itemKind());
                    if (matchContract.sourceContainerId().equals(matchContract.receiverContainerId())) break;
                    state = GoodsTradeStateSupport.reserve(state, seller.party().id(), matchContract, raw.allocations(), now);
                    reservedPrefixes.add(state);
                    reservations.add(new ProposedEvent(seller.party().id(), new GoodsTradeReserved(matchContract, raw.allocations())));
                    for (var allocation : raw.allocations()) parcels.add(new GoodsTripAssembly.Parcel(matchContract, allocation.claim(), allocation.accountId()));
                    for (var allocation : matchContract.individualAllocations().values())
                        parcels.add(new GoodsTripAssembly.Parcel(matchContract, new GoodsTripAssembly.Stock.Individual(allocation)));
                }
                if (parcels.isEmpty()) continue;
                // Bounded capacity backtracking, never cancel/re-reserve a published sale.
                // A missing animal must not forbid a smaller feasible walking shipment.
                for (int count = parcels.size(); count > 0; count--) {
                    var selected = List.copyOf(parcels.subList(0, count));
                    // Prefixes are already exact immutable reservation results for this
                    // candidate/mode. Backtracking must not replay the same drafts.
                    var selectedState = reservedPrefixes.get(count);
                    var selectedEvents = new ArrayList<>(reservations.subList(0, count));
                    var id = new SubjectId("procurement:" + WorkOpportunityIdentity.digest(seller.party().id().value()
                            + "|" + buyer.party().id().value() + "|" + mode + "|" + count
                            + "|" + endpoints.source().value() + "|" + endpoints.receiver().value()));
                    candidates.add(new Candidate(selectedState, List.copyOf(selectedEvents), seller, selected, mode,
                            offer(initial, buyer, mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? seller : buyer,
                                    id, selected, timing, now), List.copyOf(reservedPrefixes.subList(0, count + 1)), timing));
                    break;
                }
            }
            }
        }
        String preparedDecision = decision;
        return () -> finish(initial, buyer, now, requirements, candidates, preparedDecision);
    }
    private static Result finish(FrontierWorldState initial, GoodsParticipant buyer, long now,
            List<ProcurementSelection.Requirement> requirements, List<Candidate> preparedCandidates, String decision) {
        var candidates = new ArrayList<>(preparedCandidates);
        while (!candidates.isEmpty()) {
            var selected = new ProcurementSelection().select(requirements, candidates.stream().map(Candidate::offer).toList(),
                    initial.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().map(SourcingRules::labourValuePerTick).orElse(0L));
            if (selected.isEmpty()) {
                decision = "DEFERRED_FOR_CONSOLIDATION; reserved=0";
                break;
            }
            var candidate = candidates.stream().filter(c -> c.offer().id().equals(selected.orElseThrow())).findFirst().orElseThrow();
            candidates.remove(candidate);
            Optional<TransportMissionStarted> trip;
            try { trip = GoodsTripAssembly.prepare(candidate.draft(), candidate.seller(), buyer,
                    candidate.parcels(), candidate.mode(), now, candidate.timing().durationTicks()); }
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
            var declaration = trip.orElseThrow();
            var journey = declaration.mission().supplies().orElseThrow().journey();
            var exactTiming = ExpeditionTiming.assess(initial.bootstrap().ruleset().expedition(),
                    journey.outward().edges(), journey.returning().edges(), declaration.mission().organization().itinerary());
            var departure = dispatchCause(initial, buyer, candidate.mode() == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? candidate.seller() : buyer, candidate.parcels(),
                    exactTiming.deliveryTicks(), exactTiming.durationTicks(), now);
            var cost = GoodsTransportValuation.exact(initial,
                    candidate.mode() == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? candidate.seller() : buyer, declaration.mission(), now);
            if (!departure.permitsDeparture()) {
                decision = "DEFERRED_FOR_CONSOLIDATION; exactJourney=SAFE_TO_WAIT; reserved=0";
                var deferred = new ProcurementSelection.Offer(candidate.offer().id(), candidate.offer().goods(), cost.resourceExpense(),
                        exactTiming.deliveryTicks(), cost.workerTicks(), ProcurementSelection.Departure.DEFERRED);
                candidates.add(new Candidate(candidate.draft(), candidate.reservations(), candidate.seller(), candidate.parcels(),
                        candidate.mode(), deferred, candidate.prefixes(), exactTiming));
                continue;
            }
            var exactOffer = new ProcurementSelection.Offer(candidate.offer().id(), candidate.offer().goods(), cost.resourceExpense(),
                    exactTiming.deliveryTicks(), cost.workerTicks(), ProcurementSelection.Departure.READY);
            var alternatives = new ArrayList<>(candidates.stream().map(Candidate::offer).toList()); alternatives.add(exactOffer);
            var preferred = new ProcurementSelection().select(requirements, alternatives,
                    initial.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().map(SourcingRules::labourValuePerTick).orElse(0L));
            if (preferred.filter(exactOffer.id()::equals).isEmpty()) {
                candidates.add(new Candidate(candidate.draft(), candidate.reservations(), candidate.seller(), candidate.parcels(),
                        candidate.mode(), exactOffer, candidate.prefixes(), exactTiming));
                continue;
            }
            var priced = GoodsDeliveryPricing.reserve(initial, candidate.reservations(), candidate.parcels(), cost, now);
            if (priced.isEmpty()) {
                decision = "DELIVERED_PRICE_OR_FUNDS; expense=" + cost.resourceExpense().raw() + "; reserved=0";
                smaller(initial, buyer, candidate, now).ifPresent(candidates::add);
                continue;
            }
            var quotation = priced.orElseThrow(); var events = new ArrayList<>(quotation.events());
            events.addAll(GoodsTripAssembly.events(declaration, now));
            var admitted = TransportMissionProcess.admit(quotation.state(), declaration.mission().id(), declaration, now);
            return new Result(admitted, List.copyOf(events), "TRIP_ADMITTED; cause=" + departure
                    + "; parcels=" + candidate.parcels().size() + "; items=" + candidate.parcels().stream().mapToInt(p -> p.quantity()).sum()
                    + "; delivery=" + quotation.parcels().stream().mapToLong(p -> p.contract().deliveryUnitPrice().multiply(p.quantity()).raw()).sum()
                    + "; workerTicks=" + cost.workerTicks()
                    + "; members=" + declaration.group().members().size() + "; mission=" + declaration.mission().id().value());
        }
        return new Result(initial, List.of(), decision);
    }
    private static Optional<Candidate> smaller(FrontierWorldState initial, GoodsParticipant buyer, Candidate previous, long now) {
        for (int count = previous.parcels().size() - 1; count > 0; count--) {
            var parcels = List.copyOf(previous.parcels().subList(0, count));
            return Optional.of(new Candidate(previous.prefixes().get(count), List.copyOf(previous.reservations().subList(0, count)),
                    previous.seller(), parcels, previous.mode(),
                    offer(initial, buyer, previous.mode() == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? previous.seller() : buyer,
                            previous.offer().id(), parcels, previous.timing(), now), previous.prefixes(), previous.timing()));
        }
        return Optional.empty();
    }
    private static TransportOrganization.Itinerary itinerary(GoodsTradeFulfillment.Mode mode) {
        return switch (mode) {
            case SELLER_DELIVERY -> TransportOrganization.Itinerary.SOURCE_DELIVERY;
            case BUYER_COLLECTION -> TransportOrganization.Itinerary.DESTINATION_COLLECTION;
        };
    }
    private static ProcurementSelection.Offer offer(FrontierWorldState state, GoodsParticipant buyer, GoodsParticipant organizer,
            SubjectId id, List<GoodsTripAssembly.Parcel> parcels, ExpeditionTiming timing, long now) {
        var quantities = new TreeMap<String, Integer>(); var amounts = new TreeMap<String, Long>();
        for (var parcel : parcels) {
            var contract = parcel.contract();
            quantities.merge(contract.itemKind(), parcel.quantity(), Math::addExact);
            amounts.merge(contract.itemKind(), contract.goodsUnitPrice().multiply(parcel.quantity()).raw(), Math::addExact);
        }
        var lines = new TreeMap<String, ProcurementSelection.Line>();
        quantities.forEach((item, quantity) -> lines.put(item, new ProcurementSelection.Line(quantity,
                new FixedScalar(Math.ceilDiv(amounts.get(item), quantity)))));
        var cost = GoodsTransportOptions.forecast(state, organizer, timing.durationTicks(), parcels.size(), now).orElseThrow().cost();
        // Both modes use real resources. Labour remains comparative value, while
        // the exact admitted seller-delivery quote is conserved cash settlement.
        var departure = !dispatchCause(state, buyer, organizer, parcels, timing.deliveryTicks(), timing.durationTicks(), now).permitsDeparture()
                ? ProcurementSelection.Departure.DEFERRED : ProcurementSelection.Departure.READY;
        return new ProcurementSelection.Offer(id, lines, cost.resourceExpense(), timing.deliveryTicks(), cost.workerTicks(), departure);
    }
    static TripDispatchPolicy.Cause dispatchCause(FrontierWorldState state, GoodsParticipant buyer, GoodsParticipant organizer, List<GoodsTripAssembly.Parcel> parcels, long journeyTicks, long durationTicks, long now) {
        var rules = state.bootstrap().ruleset().goodsTrade(); var view = GoodsParticipantAssessment.trading(state, buyer, now);
        var quantities = new TreeMap<String, Integer>();
        for (var parcel : parcels) {
            var contract = parcel.contract(); quantities.merge(contract.itemKind(), parcel.quantity(), Math::addExact);
        }
        var amounts = new TreeMap<String, Long>();
        for (var parcel : parcels) amounts.merge(parcel.contract().itemKind(),
                parcel.contract().goodsUnitPrice().multiply(parcel.quantity()).raw(), Math::addExact);
        amounts.replaceAll((item, total) -> Math.ceilDiv(total, quantities.get(item)));
        var mode = parcels.getFirst().contract().fulfillment().mode();
        if (parcels.stream().anyMatch(parcel -> parcel.contract().fulfillment().mode() != mode))
            throw new IllegalArgumentException("mixed delivery responsibilities in one trip");
        return GoodsDispatchAdvice.assess(state, view, organizer, mode, quantities, amounts, journeyTicks, durationTicks, now);
    }
    private GoodsTripPlanning() { }
}
