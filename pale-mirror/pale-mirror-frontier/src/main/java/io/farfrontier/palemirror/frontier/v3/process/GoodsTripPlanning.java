package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.supply.*;
import io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionTiming;
import java.util.*;

/** One transaction publishes sales only after exact logistics admission has succeeded on an immutable draft. */
final class GoodsTripPlanning {
    sealed interface Choice permits Selection, Disposition { }
    enum Disposition implements Choice { AWAITING_NAVIGATION, NO_PURCHASE }
    /** Conditional choice only. No contract, reservation, crew or cargo authority. */
    record Selection(GoodsTradeParty seller, GoodsTradeParty buyer, ShipmentEndpoint source,
                     ShipmentEndpoint receiver, GoodsTradeFulfillment.Mode mode) implements Choice {
        Selection { Objects.requireNonNull(seller); Objects.requireNonNull(buyer); Objects.requireNonNull(source);
            Objects.requireNonNull(receiver); Objects.requireNonNull(mode); }
    }
    record Result(FrontierWorldState state, List<ProposedEvent> events, String decision, boolean awaitingNavigation,
                  Optional<Selection> selection) {
        Result(FrontierWorldState state, List<ProposedEvent> events, String decision, boolean awaitingNavigation) {
            this(state, events, decision, awaitingNavigation, Optional.empty());
        }
        Result(FrontierWorldState state, List<ProposedEvent> events, String decision) { this(state, events, decision, false); }
        Choice choice() { return selection.<Choice>map(value -> value).orElse(awaitingNavigation
                ? Disposition.AWAITING_NAVIGATION : Disposition.NO_PURCHASE); }
    }
    private sealed interface Alternative permits Proposal, Candidate {
        ProcurementSelection.Offer offer();
    }
    private record Proposal(GoodsParticipant seller, GoodsOrderMatching.Endpoints endpoints,
            GoodsTradeFulfillment.Mode mode, ProcurementSelection.Offer offer, ExpeditionTiming timing) implements Alternative { }
    private record Candidate(FrontierWorldState draft, List<ProposedEvent> reservations,
            GoodsParticipant seller, List<GoodsTripAssembly.Parcel> parcels, GoodsTradeFulfillment.Mode mode,
            ProcurementSelection.Offer offer, List<FrontierWorldState> prefixes, ExpeditionTiming timing) implements Alternative { }
    static Result plan(FrontierWorldState initial, GoodsParticipant reviewer, long now) {
        return ImmutablePlanningScope.within(() -> prepare(initial, reviewer, now).get());
    }
    /** Snapshot-only alternatives; only the selected candidate requests navigation evidence. */
    static java.util.function.Supplier<Result> prepare(FrontierWorldState initial, GoodsParticipant reviewer, long now) {
        return prepare(initial, reviewer, now, Optional.empty());
    }
    static java.util.function.Supplier<Result> prepare(FrontierWorldState initial, GoodsParticipant reviewer, long now,
            Optional<Choice> choice) {
        var retirement = ShipmentRetention.collect(initial, ShipmentRetention.MAX_RETIREMENTS, now);
        // Finish the disposable plan on the preparation worker as well. The
        // enclosing planning read view records route demands; its owner alone
        // acquires them and publishes the revalidated transaction.
        var planned = prepareInScope(retirement.state(), reviewer, now, choice);
        var events = new ArrayList<>(retirement.events()); events.addAll(planned.events());
        var result = new Result(planned.state(), List.copyOf(events), planned.decision(), planned.awaitingNavigation(), planned.selection());
        return () -> result;
    }
    private static Result prepareInScope(FrontierWorldState initial, GoodsParticipant reviewer, long now, Optional<Choice> choice) {
        if (choice.filter(Disposition.AWAITING_NAVIGATION::equals).isPresent())
            return new Result(initial, List.of(), "AWAITING_TRADE_CHOICE_NAVIGATION", true);
        if (choice.filter(Disposition.NO_PURCHASE::equals).isPresent())
            return new Result(initial, List.of(), "NO_ADVISED_PURCHASE");
        var selected = choice.flatMap(value -> switch (value) {
            case Selection selection -> Optional.of(selection);
            case Disposition ignored -> Optional.<Selection>empty();
        });
        if (selected.filter(value -> !value.buyer().equals(reviewer.party())).isPresent())
            throw new IllegalArgumentException("trade choice has a foreign buyer");
        if (initial.unitGroups().groups().size() >= io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState.MAX_GROUPS
                || initial.shipments().missions().size() >= io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState.MAX_GROUPS
                || initial.shipments().shipments().size() >= ShipmentState.MAX_SHIPMENTS)
            return new Result(initial, List.of(), "WAITING_FOR_TRANSPORT_RETENTION; reserved=0");
        var candidates = new ArrayList<Alternative>();
        var buyer = reviewer;
        var demand = GoodsParticipantAssessment.trading(initial, buyer, now);
        var requirements = GoodsParticipantPolicies.require(buyer).decide(demand, initial.bootstrap().ruleset().goodsTrade())
                .stream().filter(intent -> intent.side() == GoodsTradeOrder.Side.BUY)
                .map(intent -> new ProcurementSelection.Requirement(intent.itemKind(), intent.quantity(),
                        demand.supplyPlan().map(plan -> plan.require(intent.itemKind()).deadlineTicks()).orElse(Long.MAX_VALUE),
                        GoodsParticipantFunding.fundsRestart(demand, initial.bootstrap().ruleset().goodsTrade(), intent.itemKind()))).toList();
        if (requirements.isEmpty()) return new Result(initial, List.of(), "NO_CURRENT_PURCHASE_REQUIREMENT");
        var comparisons = GoodsTripComparisonFacts.forDecision(initial, buyer, now);
        var sellers = selected.map(value -> {
            var seller = initial.companies().goodsTrade().participants().participants().get(value.seller().id());
            return seller != null && seller.party().equals(value.seller()) ? List.of(seller) : List.<GoodsParticipant>of();
        }).orElseGet(() -> GoodsOrderMatching.sellers(initial, buyer, now));
        for (var seller : sellers) {
            for (var endpoints : GoodsOrderMatching.endpointPairs(initial, seller, buyer, now)) {
                if (selected.filter(value -> !value.source().equals(GoodsParticipantStorage.endpoint(initial, endpoints.source()))
                        || !value.receiver().equals(GoodsParticipantStorage.endpoint(initial, endpoints.receiver()))).isPresent()) continue;
                var lines = GoodsTripQuotes.lines(initial, seller, buyer, endpoints, now);
                if (lines.isEmpty()) continue;
                for (var mode : GoodsTradeFulfillment.Mode.values()) {
                    if (selected.filter(value -> value.mode() != mode).isPresent()) continue;
                    var forecast = ProcurementTravelForecast.between(initial, endpoints.source(), endpoints.receiver(), itinerary(mode));
                    var timing = new ExpeditionTiming(forecast.deliveryTicks(), forecast.journeyTicks());
                    var organizer = mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? seller : buyer;
                    var cost = GoodsTransportOptions.forecast(initial, organizer, timing.durationTicks(), 1, now).orElseThrow().cost();
                    var id = new SubjectId("procurement:" + WorkOpportunityIdentity.digest(seller.party().id().value()
                            + "|" + buyer.party().id().value() + "|" + mode + "|"
                            + endpoints.source().value() + "|" + endpoints.receiver().value()));
                    // Optimistic, non-binding ranking only. Exact stock, funding, lot
                    // departure and full crew cost belong to the selected draft below.
                    var ranked = comparisons.rank(new ProcurementSelection.Offer(id, lines,
                            cost.resourceExpense(), timing.deliveryTicks(), cost.workerTicks(), ProcurementSelection.Departure.READY), timing);
                    candidates.add(new Proposal(seller, endpoints, mode, ranked.offer(), ranked.timing()));
                }
            }
        }
        return finish(initial, buyer, now, requirements, candidates, comparisons, "NO_MUTUAL_KNOWN_QUOTE");
    }
    private static Optional<Candidate> reserveSelected(FrontierWorldState initial, GoodsParticipant buyer, Proposal proposal,
            long now, Map<GoodsTradeParty, Map<GoodsTradeFulfillment.Mode, Integer>> reviewsBySeller) {
        var seller = proposal.seller(); var endpoints = proposal.endpoints(); var mode = proposal.mode();
        var timing = proposal.timing(); long estimate = timing.durationTicks();
        var state = initial; var reservations = new ArrayList<ProposedEvent>(); var parcels = new ArrayList<GoodsTripAssembly.Parcel>();
        var reservedPrefixes = new ArrayList<FrontierWorldState>(); reservedPrefixes.add(initial);
        int reviews = reviewsBySeller.computeIfAbsent(seller.party(), ignored -> new EnumMap<>(GoodsTradeFulfillment.Mode.class)).getOrDefault(mode, 0); var considered = new HashSet<String>();
        int cargoLimit = Math.min(initial.bootstrap().ruleset().goodsTrade().supplyPlanning().maximumTripCargoStacks(),
                ShipmentState.MAX_SHIPMENTS - initial.shipments().shipments().size());
        while (parcels.size() < cargoLimit && state.companies().goodsTrade().contracts().size() < GoodsTradeState.MAX_CONTRACTS
                && reviews < initial.bootstrap().ruleset().goodsTrade().maximumPairReviews()) {
            var expense = mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY
                    ? GoodsTransportOptions.forecast(initial, seller, estimate, parcels.size() + 1, now).orElseThrow().cost().resourceExpense() : FixedScalar.ZERO;
            var search = GoodsOrderMatching.first(state, buyer, now, Optional.of(seller.party()), Optional.of(buyer.party()),
                    considered, Optional.of(endpoints), expense);
            reviews += search.checkedPairs();
            reviewsBySeller.get(seller.party()).put(mode, reviews);
            if (search.match().isEmpty()) {
                if (!considered.isEmpty()) { considered.clear(); continue; }
                break;
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
        if (parcels.isEmpty()) return Optional.empty();
        return Optional.of(new Candidate(state, List.copyOf(reservations), seller, List.copyOf(parcels), mode,
                offer(initial, buyer, mode == GoodsTradeFulfillment.Mode.SELLER_DELIVERY ? seller : buyer,
                        proposal.offer().id(), parcels, timing, now), List.copyOf(reservedPrefixes), timing));
    }
    private static Result finish(FrontierWorldState initial, GoodsParticipant buyer, long now,
            List<ProcurementSelection.Requirement> requirements, List<Alternative> preparedCandidates,
            GoodsTripComparisonFacts comparisons, String decision) {
        var candidates = new ArrayList<>(preparedCandidates);
        var reviewsBySeller = new HashMap<GoodsTradeParty, Map<GoodsTradeFulfillment.Mode, Integer>>();
        boolean awaitingNavigation = false;
        while (!candidates.isEmpty()) {
            var selected = new ProcurementSelection().select(requirements, candidates.stream().map(Alternative::offer).toList(),
                    initial.bootstrap().ruleset().goodsTrade().supplyPlanning().sourcing().map(SourcingRules::labourValuePerTick).orElse(0L));
            if (selected.isEmpty()) {
                decision = "DEFERRED_FOR_CONSOLIDATION; reserved=0";
                break;
            }
            var alternative = candidates.stream().filter(c -> c.offer().id().equals(selected.orElseThrow())).findFirst().orElseThrow();
            candidates.remove(alternative);
            if (alternative instanceof Proposal proposal) {
                var reserved = reserveSelected(initial, buyer, proposal, now, reviewsBySeller);
                if (reserved.isEmpty()) decision = "CURRENT_RESERVE_STOCK_OR_FUNDS; reserved=0";
                else {
                    var value = reserved.orElseThrow(); var measured = comparisons.rank(value.offer(), value.timing());
                    candidates.add(measured.offer().goods().equals(value.offer().goods())
                            ? new Candidate(value.draft(), value.reservations(), value.seller(), value.parcels(), value.mode(),
                                measured.offer(), value.prefixes(), measured.timing()) : value);
                }
                continue;
            }
            var candidate = (Candidate) alternative;
            Optional<TransportMissionStarted> trip;
            try { trip = GoodsTripAssembly.prepare(candidate.draft(), candidate.seller(), buyer,
                    candidate.parcels(), candidate.mode(), now, candidate.timing().durationTicks()); }
            catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable unavailable) {
                decision = "WAITING_FOR_SELECTED_TRIP; status=" + unavailable.status() + "; reserved=0";
                // One selected pending preparation, never full-route fan-out for all quotes.
                if (unavailable.status() == io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING)
                    return new Result(initial, List.of(), decision, true);
                if (unavailable.status() == io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.UNKNOWN_GEOMETRY)
                    awaitingNavigation = true;
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
                comparisons.remember(deferred, exactTiming);
                candidates.add(new Candidate(candidate.draft(), candidate.reservations(), candidate.seller(), candidate.parcels(),
                        candidate.mode(), deferred, candidate.prefixes(), exactTiming));
                continue;
            }
            var exactOffer = new ProcurementSelection.Offer(candidate.offer().id(), candidate.offer().goods(), cost.resourceExpense(),
                    exactTiming.deliveryTicks(), cost.workerTicks(), ProcurementSelection.Departure.READY);
            comparisons.remember(exactOffer, exactTiming);
            var alternatives = new ArrayList<>(candidates.stream().map(Alternative::offer).toList()); alternatives.add(exactOffer);
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
                    + "; members=" + declaration.group().members().size() + "; mission=" + declaration.mission().id().value(), false,
                    Optional.of(new Selection(candidate.seller().party(), buyer.party(),
                            GoodsParticipantStorage.endpoint(initial, declaration.mission().sender().containerId()),
                            GoodsParticipantStorage.endpoint(initial, declaration.mission().receiver().containerId()), candidate.mode())));
        }
        return new Result(initial, List.of(), decision, awaitingNavigation);
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
