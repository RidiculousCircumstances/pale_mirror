package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Registered bounded economic reviews. Each participant supplies consent; matching and delivery remain separate. */
public final class GoodsParticipantProcess {
    public static final String REVIEW = "frontier.goods.participant.review";
    public static ScheduledAction review(SubjectId party, long due) {
        return new ScheduledAction(new ScheduleId("schedule:goods-participant/" + party.value().replace(':', '-')),
                new SimInstant(due), 0, party, REVIEW, 1);
    }
    /** Due time orders the queue; only the supplied execution instant authorizes current trades. */
    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, SimInstant executionInstant) {
        Objects.requireNonNull(executionInstant, "goods review execution instant");
        if (executionInstant.compareTo(action.dueAt()) < 0)
            throw new IllegalArgumentException("goods participant review cannot execute before its due time");
        boolean periodic = action.kind().equals(REVIEW);
        if (periodic ? !action.id().equals(review(action.subject(), action.dueAt().ticks()).id())
                : !action.kind().equals(GoodsParticipantWakeup.OPPORTUNITY) || !action.id().value().startsWith("schedule:goods-opportunity/"))
            throw new IllegalArgumentException("goods participant review has a foreign schedule");
        var participant = state.companies().goodsTrade().participants().participants().get(action.subject());
        if (participant == null) throw new IllegalArgumentException("goods review lost its exact declared participant");
        long now = executionInstant.ticks();
        var events = new ArrayList<ProposedEvent>();
        // Retire only obligations already terminal in this transaction's pre-state.
        // Newly withdrawn promises must retain their settlement until a later review.
        var retirement = closedRetirement(state, now);
        if (retirement.isPresent()) {
            events.add(new ProposedEvent(GoodsTradeMarketIdentity.OWNER, retirement.orElseThrow()));
            state = GoodsTradeStateSupport.retire(state, GoodsTradeMarketIdentity.OWNER, retirement.orElseThrow(), now);
        }
        var commitments = GoodsCommitmentReview.plan(state, participant);
        state = commitments.state(); events.addAll(commitments.events());
        for (var withdrawal : commitments.events()) {
            var disposition = ((GoodsTradeCancelled) withdrawal.payload()).disposition();
            var contract = state.companies().goodsTrade().contracts().get(disposition.contractId());
            events.addAll(GoodsParticipantWakeup.party(state, contract.buyer().id(), disposition.id().value(), now));
        }
        // Same-container purchases transfer title only. Existing exact receiving custody
        // and the buyer's authorized order, not a fake round-trip, justify acceptance.
        for (var contract : state.companies().goodsTrade().contracts().values().stream()
                .filter(value -> !value.terminal() && value.buyer().equals(participant.party())
                        && value.sourceContainerId().equals(value.receiverContainerId()))
                .sorted(Comparator.comparing(GoodsTradeContract::id)).toList()) {
            if (ReferenceContainerCustody.blocksCanonicalUse(state, contract.receiverContainerId())) continue;
            var account = FungibleResourceCustodySupport.accountAtContainer(state, contract.receiverContainerId()).orElseThrow();
            for (var claimId : contract.outstandingClaims().keySet().stream().sorted().toList()) {
                if (!account.claimQuantities().containsKey(claimId)) continue;
                var claim = state.inventory().fungibleResources().claims().get(claimId);
                var splits = new HashMap<SubjectId, SubjectId>();
                for (var lot : claim.lotQuantities().entrySet()) if (state.inventory().fungibleResources().lots().get(lot.getKey()).quantity() > lot.getValue())
                    splits.put(lot.getKey(), new SubjectId("lot:goods-local/" + WorkOpportunityIdentity.digest(contract.id().value()
                            + "|" + contract.revision() + "|" + lot.getKey().value())));
                var receipt = new GoodsTradeAcceptance(new SubjectId("receipt:goods-local/" + contract.id().value().replace(':', '-') + "/" + contract.revision()),
                        contract.id(), contract.revision(), new ResourceTitleTransfer(account.id(), claim.id(), contract.seller().id(), contract.buyer().id(), claim.lotQuantities(), splits));
                state = GoodsTradeStateSupport.accept(state, participant.party().id(), receipt);
                events.add(new ProposedEvent(participant.party().id(), new GoodsTradeAccepted(receipt)));
                events.addAll(GoodsParticipantWakeup.party(state, contract.seller().id(), receipt.id().value(), now));
                break; // One exact reception per contract/review.
            }
        }
        var rules = state.bootstrap().ruleset().goodsTrade();
        var intents = GoodsParticipantPolicies.require(participant).decide(GoodsParticipantView.read(state, participant), rules);
        int published = 0;
        for (var intent : intents) for (var peer : participant.known()) {
            var counterparty = state.companies().goodsTrade().participants().participants().get(peer.party().id());
            if (counterparty == null || !counterparty.party().equals(peer.party()) || !counterparty.endpoint().equals(peer.endpoint())) continue;
            boolean pending = state.companies().goodsTrade().orders().values().stream().anyMatch(order ->
                    order.party().equals(participant.party()) && order.counterparty().equals(peer.party()) && order.itemKind().equals(intent.itemKind())
                            && order.side() == intent.side() && order.availableQuantity() > 0 && order.expiresAtTick() >= now);
            if (pending || state.companies().goodsTrade().orders().size() >= GoodsTradeState.MAX_ORDERS) continue;
            var id = new SubjectId("order:goods-policy/" + WorkOpportunityIdentity.digest(participant.party().id().value() + "|" + participant.reviewRevision()
                    + "|" + peer.party().id().value() + "|" + intent.itemKind() + "|" + intent.side().wireTag()));
            var order = new GoodsTradeOrder(id, participant.party(), peer.party(), intent.side(), participant.endpoint().containerId(),
                    intent.itemKind(), intent.quantity(), 0, intent.limit(), Math.addExact(now, rules.orderLifetime()));
            state = GoodsTradeStateSupport.place(state, participant.party().id(), order, now);
            events.add(new ProposedEvent(participant.party().id(), new GoodsTradeOrderPlaced(order))); published++;
        }
        var search = state.companies().goodsTrade().contracts().size() < GoodsTradeState.MAX_CONTRACTS
                ? GoodsOrderMatching.first(state, participant, now) : new GoodsOrderMatching.Search(Optional.empty(), "CONTRACT_RETENTION_LIMIT", 0);
        var match = search.match();
        String decision = search.disposition() + "; pairs=" + search.checkedPairs() + "; published=" + published;
        if (participant.known().isEmpty()) decision = "NO_KNOWN_COUNTERPARTY";
        if (match.isPresent()) {
            var value = match.orElseThrow();
            var seller = state.companies().goodsTrade().participants().participants().get(value.contract().seller().id());
            var buyer = state.companies().goodsTrade().participants().participants().get(value.contract().buyer().id());
            var routeStatus = GoodsShipmentPlanning.routeStatus(state, seller.endpoint(), buyer.endpoint(), value.contract().id());
            if (routeStatus == io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.FOUND) {
                state = GoodsTradeStateSupport.reserve(state, value.contract().seller().id(), value.contract(), value.allocations(), now);
                events.add(new ProposedEvent(value.contract().seller().id(), new GoodsTradeReserved(value.contract(), value.allocations())));
                decision = "CONTRACT_RESERVED; contract=" + value.contract().id().value();
                var dispatch = GoodsShipmentPlanning.dispatch(state, seller, now); events.addAll(dispatch);
                if (!value.contract().sourceContainerId().equals(value.contract().receiverContainerId()) && dispatch.isEmpty()) decision = "RESERVED_AWAITING_AVAILABLE_COURIER";
            } else decision = "NAVIGATION_" + routeStatus.name();
        } else events.addAll(GoodsShipmentPlanning.dispatch(state, participant, now));
        decision += "; withdrawn=" + commitments.events().stream().mapToInt(event ->
                    ((GoodsTradeCancelled) event.payload()).disposition().quantity()).sum()
                + "; reasons=" + commitments.events().stream().map(event ->
                    ((GoodsTradeCancelled) event.payload()).disposition().reason().name()).distinct().toList()
                + "; " + GoodsParticipantPolicies.require(participant).explain(GoodsParticipantView.read(state, participant), rules);
        if (decision.length() > 512) decision = decision.substring(0, 512);
        events.add(new ProposedEvent(participant.party().id(), new GoodsParticipantReviewed(participant.reviewRevision(), decision, now)));
        if (periodic) events.add(new ProposedEvent(participant.party().id(), new ScheduleEffect.Created(review(participant.party().id(), Math.addExact(now, rules.reviewInterval())))));
        if (published > 0) for (var peer : participant.known())
            events.addAll(GoodsParticipantWakeup.party(state, peer.party().id(), "quote|" + participant.party().id().value()
                    + "|" + participant.reviewRevision(), now));
        return List.copyOf(events);
    }
    private static Optional<GoodsTradeRetired> closedRetirement(FrontierWorldState state, long now) {
        var trade = state.companies().goodsTrade();
        for (var order : trade.orders().values().stream().filter(value -> value.expiresAtTick() < now)
                .sorted(Comparator.comparing(GoodsTradeOrder::id)).toList()) {
            var orders = new HashSet<SubjectId>(); var contracts = new HashSet<SubjectId>(); orders.add(order.id());
            boolean expanded;
            do {
                expanded = false;
                for (var contract : trade.contracts().values()) if (orders.contains(contract.sellOrderId()) || orders.contains(contract.buyOrderId())) {
                    expanded |= contracts.add(contract.id()); expanded |= orders.add(contract.sellOrderId()); expanded |= orders.add(contract.buyOrderId());
                }
            } while (expanded);
            if (orders.stream().anyMatch(id -> trade.orders().get(id).expiresAtTick() >= now)
                    || contracts.stream().anyMatch(id -> !trade.contracts().get(id).terminal())
                    || state.physicalIntents().values().stream().anyMatch(intent -> contracts.contains(intent.causeSubjectId())
                            || orders.contains(intent.causeSubjectId()))
                    || state.shipments().shipments().values().stream().anyMatch(s -> (!s.terminal() || s.reception().isPresent()) && contracts.contains(s.authorization().claimantId()))) continue;
            return Optional.of(new GoodsTradeRetired(orders, contracts));
        }
        return Optional.empty();
    }
    private GoodsParticipantProcess() { }
}
