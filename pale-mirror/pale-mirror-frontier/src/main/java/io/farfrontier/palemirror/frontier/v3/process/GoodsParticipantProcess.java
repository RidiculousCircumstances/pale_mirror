package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Registered bounded economic reviews. Each participant supplies consent; matching and delivery remain separate. */
public final class GoodsParticipantProcess {
    static FrontierScheduledPlanner planner() {
        return new FrontierScheduledPlanner() {
            @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
                throw new IllegalArgumentException("economic review requires its execution instant");
            }
            @Override public Optional<io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAdvisory<FrontierWorldState>> advice(
                    FrontierWorldState state, ScheduledAction action, SimInstant instant) {
                return action.kind().equals(REVIEW) ? Optional.of(GoodsParticipantReviewAdvice.capture(state, action, instant))
                        : action.kind().equals(GoodsParticipantWakeup.OPPORTUNITY)
                            ? Optional.of(GoodsParticipantOpportunityAdvice.capture(state, action, instant)) : Optional.empty();
            }
            @Override public Optional<ScheduledPreparation> preparation(FrontierWorldState state, ScheduledAction action, SimInstant instant) {
                return action.kind().equals(GoodsParticipantWakeup.OPPORTUNITY)
                        ? Optional.of(() -> prepare(state, action, instant)) : Optional.empty();
            }
            @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action,
                    io.farfrontier.palemirror.frontier.v3.api.SimInstant instant) {
                return GoodsParticipantProcess.plan(state, action, instant);
            }
        };
    }
    private record QuoteKey(String itemKind, GoodsTradeOrder.Side side, SubjectId container) { }
    private record Quote(GoodsParticipantPolicy.Intent intent, GoodsParticipantStorage.QuoteEndpoint allocation) { }
    public static final String REVIEW = "frontier.goods.participant.review";
    public static ScheduledAction review(SubjectId party, long due) {
        return new ScheduledAction(new ScheduleId("schedule:goods-participant/" + party.value().replace(':', '-')),
                new SimInstant(due), 0, party, REVIEW, 1);
    }
    /** Structural workload, not measured host time or a trade permission.
     * Full forecasting reads known peers; matching-only work visits actual opposing sellers. */
    static int reviewWorkUnits(FrontierWorldState state, ScheduledAction action) {
        var participant = state.companies().goodsTrade().participants().participants().get(action.subject());
        if (participant == null) throw new IllegalArgumentException("goods review lost its declared participant");
        int modes = state.bootstrap().ruleset().goodsTrade().supplyPlanning().enabled()
                ? GoodsTradeFulfillment.Mode.values().length : 1;
        int peers = action.kind().equals(GoodsParticipantWakeup.OPPORTUNITY)
                ? GoodsOrderMatching.sellers(state, participant, action.dueAt().ticks()).size() : participant.known().size();
        return Math.addExact(1, Math.multiplyExact(peers, modes));
    }
    /** Due time orders the queue; only the supplied execution instant authorizes current trades. */
    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, SimInstant executionInstant) {
        return ImmutablePlanningScope.within(() -> prepare(state, action, executionInstant).get());
    }
    static java.util.function.Supplier<List<ProposedEvent>> prepare(FrontierWorldState state, ScheduledAction action, SimInstant executionInstant) {
        var participant = Objects.requireNonNull(state.companies().goodsTrade().participants().participants().get(action.subject()), "trade opportunity owner");
        // A current quote is a matching opportunity, not a request to solve the
        // entire economy again. Existing consent is still checked against current
        // resources/cash/expiry; hard invalidations schedule their own full review.
        var assessment = action.kind().equals(GoodsParticipantWakeup.OPPORTUNITY)
                ? new SettlementEconomicPlanPreparation.Review(executionInstant.ticks(), participant.prices(), Optional.empty())
                : SettlementEconomicPlanPreparation.review(state, action, executionInstant);
        return prepare(state, action, executionInstant, assessment);
    }
    static java.util.function.Supplier<List<ProposedEvent>> prepare(FrontierWorldState state, ScheduledAction action,
            SimInstant executionInstant, SettlementEconomicPlanPreparation.Review advice) {
        return prepareDecision(state, action, executionInstant, advice, Optional.empty()).completion();
    }
    record PreparedDecision(java.util.function.Supplier<List<ProposedEvent>> completion, GoodsTripPlanning.Choice choice) { }
    static PreparedDecision prepareDecision(FrontierWorldState state, ScheduledAction action,
            SimInstant executionInstant, SettlementEconomicPlanPreparation.Review advice, Optional<GoodsTripPlanning.Choice> choice) {
        Objects.requireNonNull(executionInstant, "goods review execution instant");
        if (executionInstant.compareTo(action.dueAt()) < 0)
            throw new IllegalArgumentException("goods participant review cannot execute before its due time");
        boolean periodic = action.kind().equals(REVIEW);
        if (periodic ? !action.id().equals(review(action.subject(), action.dueAt().ticks()).id())
                : !action.kind().equals(GoodsParticipantWakeup.OPPORTUNITY) || !action.id().value().startsWith("schedule:goods-opportunity/"))
            throw new IllegalArgumentException("goods participant review has a foreign schedule");
        var registered = state.companies().goodsTrade().participants().participants().get(action.subject());
        if (registered == null) throw new IllegalArgumentException("goods review lost its exact declared participant");
        long now = executionInstant.ticks();
        var events = new ArrayList<ProposedEvent>();
        var previousParticipant = registered;
        var rules = state.bootstrap().ruleset().goodsTrade();
        if (!advice.prices().equals(previousParticipant.prices())) {
            var review = new GoodsParticipantPricesReviewed(previousParticipant.reviewRevision(), advice.atTick(), advice.prices());
            events.add(new ProposedEvent(previousParticipant.party().id(), review));
            state = GoodsParticipantPricing.applyCommitted(state, previousParticipant.party().id(), review, now);
        }
        var participant = state.companies().goodsTrade().participants().participants().get(action.subject());
        if (advice.publication().isPresent()) {
            var publication = advice.publication().orElseThrow();
            events.add(new ProposedEvent(participant.party().id(), new SettlementEconomicPlanPublished(publication)));
            state = SettlementEconomicPublication.apply(state, participant.party().id(), publication, now);
            if (publication.cultivation().isPresent()) events.add(new ProposedEvent(participant.party().id(),
                    new ScheduleEffect.ReconsiderationRequested(CultivationProcessModule.next(participant.party().id(), Math.addExact(now, 1)))));
        }
        // Retire only obligations already terminal in this transaction's pre-state.
        // Newly withdrawn promises must retain their settlement until a later review.
        var retirement = closedRetirement(state, now);
        if (retirement.isPresent()) {
            events.add(new ProposedEvent(GoodsTradeMarketIdentity.OWNER, retirement.orElseThrow()));
            state = GoodsTradeStateSupport.retire(state, GoodsTradeMarketIdentity.OWNER, retirement.orElseThrow(), now);
        }
        // Same-container purchases transfer title only. Existing exact receiving custody
        // and the buyer's authorized order, not a fake round-trip, justify acceptance.
        for (var contract : state.companies().goodsTrade().contracts().values().stream()
                .filter(value -> !value.terminal() && value.buyer().equals(previousParticipant.party())
                        && value.sourceContainerId().equals(value.receiverContainerId()))
                .sorted(Comparator.comparing(GoodsTradeContract::id)).toList()) {
            if (ReferenceContainerCustody.blocksCanonicalUse(state, contract.receiverContainerId())) continue;
            for (var claimId : contract.outstandingClaims().keySet().stream().sorted().toList()) {
                var title = GoodsTitleAccounting.local(state, contract, claimId);
                if (title.isEmpty()) continue;
                var receipt = new GoodsTradeAcceptance(new SubjectId("receipt:goods-local/" + contract.id().value().replace(':', '-') + "/" + contract.revision()),
                        contract.id(), contract.revision(), title.orElseThrow());
                state = GoodsTradeStateSupport.accept(state, previousParticipant.party().id(), receipt, now);
                events.add(new ProposedEvent(previousParticipant.party().id(), new GoodsTradeAccepted(receipt)));
                events.addAll(GoodsParticipantWakeup.party(state, contract.seller().id(), receipt.id().value(), now));
                break; // One exact reception per contract/review.
            }
        }
        var commitments = GoodsCommitmentReview.plan(state, previousParticipant, now);
        state = commitments.state(); events.addAll(commitments.events());
        for (var withdrawal : commitments.events()) {
            var disposition = ((GoodsTradeCancelled) withdrawal.payload()).disposition();
            var contract = state.companies().goodsTrade().contracts().get(disposition.contractId());
            events.addAll(GoodsParticipantWakeup.party(state, contract.buyer().id(), disposition.id().value(), now));
        }
        var intents = GoodsParticipantPolicies.require(participant).decide(GoodsParticipantAssessment.trading(state, participant, now), rules);
        var desired = new LinkedHashMap<QuoteKey, Quote>();
        for (var intent : intents) for (var allocation : GoodsParticipantStorage.quoteEndpoints(state, participant, intent))
            desired.put(new QuoteKey(intent.itemKind(), intent.side(), allocation.endpoint().containerId()), new Quote(intent, allocation));
        var retainedExpiry = new HashMap<QuoteKey, Long>();
        for (var order : state.companies().goodsTrade().orders().values().stream()
                .filter(value -> value.party().equals(participant.party()) && value.availableQuantity() > 0)
                .sorted(Comparator.comparing(GoodsTradeOrder::id)).toList()) {
            var key = new QuoteKey(order.itemKind(), order.side(), order.containerId());
            var replacement = desired.get(key);
            if (order.expiresAtTick() >= now && replacement != null
                    && replacement.allocation().quantity() == order.availableQuantity()
                    && replacement.intent().limit().equals(order.unitPriceLimit())) continue;
            if (order.expiresAtTick() >= now && replacement != null) retainedExpiry.merge(key, order.expiresAtTick(), Math::min);
            var withdrawal = new GoodsTradeOrderWithdrawn(order.id(), order.committedQuantity());
            events.add(new ProposedEvent(participant.party().id(), withdrawal));
            state = state.withCompanies(state.companies().withGoodsTrade(state.companies().goodsTrade().withdraw(participant.party().id(), withdrawal)));
        }
        var pendingQuotes = new HashSet<QuoteKey>();
        for (var order : state.companies().goodsTrade().orders().values())
            if (order.party().equals(participant.party()) && order.availableQuantity() > 0 && order.expiresAtTick() >= now)
                pendingQuotes.add(new QuoteKey(order.itemKind(), order.side(), order.containerId()));
        int published = 0;
        for (var entry : desired.entrySet()) {
            var key = entry.getKey(); var quote = entry.getValue(); var intent = quote.intent(); var allocation = quote.allocation();
            if (pendingQuotes.contains(key) || state.companies().goodsTrade().orders().size() >= GoodsTradeState.MAX_ORDERS) continue;
            var inputRevision = state.companies().goodsTrade().participants().economicState(participant.party().id()).inputRevision();
            var id = new SubjectId("order:goods-policy/" + WorkOpportunityIdentity.digest(participant.party().id().value() + "|"
                    + participant.reviewRevision() + "|" + inputRevision + "|" + action.id().value() + "|" + now
                    + "|" + intent.itemKind() + "|" + intent.side().wireTag() + "|" + key.container().value()));
            var order = new GoodsTradeOrder(id, participant.party(), intent.side(), key.container(), intent.itemKind(),
                    allocation.quantity(), 0, intent.limit(), retainedExpiry.getOrDefault(key, Math.addExact(now, rules.orderLifetime())));
            state = GoodsTradeStateSupport.place(state, participant.party().id(), order, now);
            events.add(new ProposedEvent(participant.party().id(), new GoodsTradeOrderPlaced(order)));
            pendingQuotes.add(key); published++;
        }
        var trip = rules.supplyPlanning().enabled() ? Optional.of(GoodsTripPlanning.prepare(state, participant, now, choice))
                : Optional.<java.util.function.Supplier<GoodsTripPlanning.Result>>empty();
        var preparedState = state; int preparedPublished = published;
        return new PreparedDecision(() -> finish(preparedState, action, participant, now, events, commitments, preparedPublished, trip),
                trip.map(value -> value.get().choice()).orElse(GoodsTripPlanning.Disposition.NO_PURCHASE));
    }
    private static List<ProposedEvent> finish(FrontierWorldState state, ScheduledAction action, GoodsParticipant participant,
            long now, List<ProposedEvent> preparedEvents, GoodsCommitmentReview.Result commitments, int published,
            Optional<java.util.function.Supplier<GoodsTripPlanning.Result>> preparedTrip) {
        var events = new ArrayList<>(preparedEvents);
        var rules = state.bootstrap().ruleset().goodsTrade();
        boolean periodic = action.kind().equals(REVIEW);
        String decision; boolean awaitingNavigation = false;
        if (rules.supplyPlanning().enabled()) {
            var trip = preparedTrip.orElseThrow().get();
            state = trip.state(); events.addAll(trip.events()); decision = trip.decision() + "; published=" + published;
            awaitingNavigation = trip.awaitingNavigation();
        } else {
        var search = state.companies().goodsTrade().contracts().size() < GoodsTradeState.MAX_CONTRACTS
                ? GoodsOrderMatching.first(state, participant, now) : new GoodsOrderMatching.Search(Optional.empty(), "CONTRACT_RETENTION_LIMIT", 0);
        var match = search.match();
        decision = search.disposition() + "; pairs=" + search.checkedPairs() + "; published=" + published;
        if (participant.known().isEmpty()) decision = "NO_KNOWN_COUNTERPARTY";
        if (match.isPresent()) {
            var value = match.orElseThrow();
            var seller = state.companies().goodsTrade().participants().participants().get(value.contract().seller().id());
            var buyer = state.companies().goodsTrade().participants().participants().get(value.contract().buyer().id());
            var routeStatus = GoodsShipmentPlanning.routeStatus(state,
                    GoodsParticipantStorage.endpoint(state, value.contract().sourceContainerId()),
                    GoodsParticipantStorage.endpoint(state, value.contract().receiverContainerId()), value.contract().id());
            if (routeStatus == io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.FOUND) {
                state = GoodsTradeStateSupport.reserve(state, value.contract().seller().id(), value.contract(), value.allocations(), now);
                events.add(new ProposedEvent(value.contract().seller().id(), new GoodsTradeReserved(value.contract(), value.allocations())));
                decision = "CONTRACT_RESERVED; contract=" + value.contract().id().value();
                var dispatch = GoodsShipmentPlanning.dispatch(state, seller, now); events.addAll(dispatch);
                if (!value.contract().sourceContainerId().equals(value.contract().receiverContainerId()) && dispatch.isEmpty()) decision = "RESERVED_AWAITING_AVAILABLE_COURIER";
            } else {
                decision = "NAVIGATION_" + routeStatus.name();
                awaitingNavigation = routeStatus == io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.PLANNING
                        || routeStatus == io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.UNKNOWN_GEOMETRY;
            }
        } else events.addAll(GoodsShipmentPlanning.dispatch(state, participant, now));
        }
        if (periodic) {
            decision += "; withdrawn=" + commitments.events().stream().mapToInt(event ->
                        ((GoodsTradeCancelled) event.payload()).disposition().quantity()).sum()
                    + "; reasons=" + commitments.events().stream().map(event ->
                        ((GoodsTradeCancelled) event.payload()).disposition().reason().name()).distinct().toList()
                    + "; " + GoodsParticipantPolicies.require(participant).explain(GoodsParticipantAssessment.trading(state, participant, now), rules);
            if (decision.length() > 512) decision = decision.substring(0, 512);
            events.add(new ProposedEvent(participant.party().id(), new GoodsParticipantReviewed(participant.reviewRevision(), decision, now)));
            long nextReview = state.companies().goodsTrade().participants().economicState(participant.party().id()).accepted()
                    .map(SettlementEconomicPlan::reviewAtTick).orElse(Math.addExact(now, rules.reviewInterval()));
            events.add(new ProposedEvent(participant.party().id(), new ScheduleEffect.ReconsiderationRequested(
                    review(participant.party().id(), Math.max(Math.addExact(now, 1), nextReview)))));
        }
        events.addAll(GoodsParticipantWakeup.quoted(state, preparedEvents.stream()
                .map(ProposedEvent::payload).filter(GoodsTradeOrderPlaced.class::isInstance)
                .map(GoodsTradeOrderPlaced.class::cast).map(GoodsTradeOrderPlaced::order).toList(), now));
        if (awaitingNavigation) {
            var retry = GoodsParticipantWakeup.action(participant.party().id(), Math.addExact(now, 1));
            events.add(new ProposedEvent(participant.party().id(), periodic
                    ? new ScheduleEffect.ReconsiderationRequested(retry) : new ScheduleEffect.Rescheduled(action.id(), retry)));
        }
        if (events.isEmpty()) events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        return List.copyOf(events);
    }
    private static Optional<GoodsTradeRetired> closedRetirement(FrontierWorldState state, long now) {
        return GoodsTradeRetention.collect(state, now);
    }
    private GoodsParticipantProcess() { }
}
