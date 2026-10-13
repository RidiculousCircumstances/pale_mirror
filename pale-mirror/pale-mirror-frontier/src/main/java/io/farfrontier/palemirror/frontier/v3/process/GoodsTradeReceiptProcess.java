package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** The consenting buyer accepts actually received goods; transport never awards title or payment. */
public final class GoodsTradeReceiptProcess {
    public static final String REVIEW = "frontier.goods_trade.receipt_review";
    public static ScheduledAction review(SubjectId contract, SubjectId receipt, long atTick) {
        return new ScheduledAction(new ScheduleId("schedule:goods-receipt/" + token(contract, 0, receipt)),
                new SimInstant(atTick), 12, contract, REVIEW, 1);
    }
    public static ProposedEvent wake(SubjectId contract, SubjectId receipt, long atTick) {
        var action = review(contract, receipt, Math.addExact(atTick, 1));
        return new ProposedEvent(contract, new ScheduleEffect.Created(action));
    }
    public static List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action, SimInstant executionInstant) {
        Objects.requireNonNull(executionInstant, "goods receipt execution instant");
        if (executionInstant.compareTo(action.dueAt()) < 0)
            throw new IllegalArgumentException("goods receipt cannot execute before its due time");
        long now = executionInstant.ticks();
        if (!action.kind().equals(REVIEW))
            throw new IllegalArgumentException("goods receipt review has a foreign schedule declaration");
        var cancelled = new ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id()));
        GoodsTradeContract contract = state.companies().goodsTrade().contracts().get(action.subject());
        if (contract == null || contract.terminal()) return List.of(cancelled);
        var ledger = state.inventory().fungibleResources();
        if (contract.fulfillment().mode() == GoodsTradeFulfillment.Mode.BUYER_COLLECTION)
            return collect(state, action, contract, now, cancelled);
        for (Shipment shipment : state.shipments().shipments().values().stream().sorted(Comparator.comparing(Shipment::id)).toList()) {
            if (shipment.authorization().kind() != ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT
                    || !shipment.authorization().claimantId().equals(contract.id()) || shipment.reception().isEmpty()) continue;
                var reception = shipment.reception().orElseThrow();
                if (!action.id().equals(review(contract.id(), reception.id(), action.dueAt().ticks()).id())) continue;
                SubjectId claimId = reception.claimId();
                CustodyAccount account = ledger.accounts().get(shipment.receivingAccountId());
                if (account == null || !account.custody().equals(new ResourceCustody.Container(contract.receiverContainerId()))
                        || account.claimQuantities().getOrDefault(claimId, 0) != reception.quantity())
                    throw new IllegalArgumentException("transport reception lost its exact received allocation");
                var receiptId = reception.id();
                var children = new LinkedHashMap<SubjectId, SubjectId>();
                for (var portion : reception.lotQuantities().entrySet())
                    if (ledger.lots().get(portion.getKey()).quantity() > portion.getValue())
                        children.put(portion.getKey(), new SubjectId("lot:goods-" + token(receiptId, contract.revision(), portion.getKey())));
                var receipt = new GoodsTradeAcceptance(receiptId, contract.id(), contract.revision(),
                        new ResourceTitleTransfer(account.id(), claimId, contract.seller().id(), contract.buyer().id(), reception.lotQuantities(), children));
                var acknowledged = new ShipmentReceiptAcknowledged(shipment.id(), shipment.revision(), reception.id());
                ShipmentStateSupport.acknowledge(GoodsTradeStateSupport.accept(state, contract.buyer().id(), receipt, now), shipment.id(), acknowledged);
                var events = new ArrayList<>(List.of(new ProposedEvent(contract.buyer().id(), new GoodsTradeAccepted(receipt)),
                        new ProposedEvent(shipment.id(), acknowledged), ShipmentProcess.wake(shipment.id(), now),
                        cancelled));
                events.addAll(GoodsParticipantWakeup.party(state, contract.buyer().id(), receipt.id().value(), now));
                events.addAll(GoodsParticipantWakeup.party(state, contract.seller().id(), receipt.id().value(), now));
                return List.copyOf(events);
        }
        // A notice with no currently received allocation does not invent delivery or poll forever.
        // The next exact unload publishes another receiver review notice.
        return List.of(cancelled);
    }
    private static UUID token(SubjectId owner, long revision, SubjectId operand) {
        return UUID.nameUUIDFromBytes((owner.value() + "\n" + revision + "\n" + operand.value()).getBytes(StandardCharsets.UTF_8));
    }
    private static List<ProposedEvent> collect(FrontierWorldState state, ScheduledAction action, GoodsTradeContract contract,
                                               long now, ProposedEvent cancelled) {
        for (var shipment : state.shipments().shipments().values().stream().sorted(Comparator.comparing(Shipment::id)).toList()) {
            if (shipment.authorization().kind() != ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT
                    || !shipment.authorization().claimantId().equals(contract.id()) || shipment.status() != Shipment.Status.CARRYING
                    || !action.id().equals(review(contract.id(), shipment.id(), action.dueAt().ticks()).id())) continue;
            var receiptId = new SubjectId("receipt:goods-pickup/" + token(shipment.id(), 0, contract.id()));
            var children = new LinkedHashMap<SubjectId, SubjectId>(); var resources = state.inventory().fungibleResources();
            shipment.lotQuantities().forEach((lot, quantity) -> {
                if (resources.lots().get(lot).quantity() > quantity)
                    children.put(lot, new SubjectId("lot:goods-" + token(receiptId, contract.revision(), lot)));
            });
            var receipt = new GoodsTradeAcceptance(receiptId, contract.id(), contract.revision(), new ResourceTitleTransfer(
                    shipment.carriedAccountId(), shipment.authorization().claimId(), contract.seller().id(), contract.buyer().id(),
                    shipment.lotQuantities(), children));
            var collected = new GoodsTradeCollected(shipment.id(), shipment.revision(), receipt);
            GoodsTradeCollectionSupport.accept(state, contract.buyer().id(), collected, now);
            var events = new ArrayList<>(List.of(new ProposedEvent(contract.buyer().id(), collected), cancelled,
                    TransportMissionProcess.wake(shipment.transportMissionId().orElseThrow(), now)));
            events.addAll(GoodsParticipantWakeup.party(state, contract.buyer().id(), receiptId.value(), now));
            events.addAll(GoodsParticipantWakeup.party(state, contract.seller().id(), receiptId.value(), now));
            return List.copyOf(events);
        }
        return List.of(cancelled);
    }
    private GoodsTradeReceiptProcess() { }
}
