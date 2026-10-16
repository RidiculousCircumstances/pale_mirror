package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FrontierScheduleView;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.stream.Collectors;

/** Read-only public/company policy, stock and obligation join. It grants no consent or title. */
final class FrontierV3GoodsParticipantDiagnosticJson {
    static String render(FrontierScheduleView checkpoint, FrontierWorldState state, GoodsParticipant participant) {
        var view = GoodsParticipantView.read(state, participant);
        var stocks = view.stocks().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).map(entry ->
                "{\"commodity\":" + string(entry.getKey()) + ",\"owned\":" + entry.getValue().ownedInStorage()
                        + ",\"unclaimed\":" + entry.getValue().unclaimedInStorage() + ",\"incoming\":" + entry.getValue().expectedIncoming()
                        + ",\"protectedMinimum\":" + entry.getValue().protectedMinimum() + "}").collect(Collectors.joining(","));
        var trade = state.companies().goodsTrade();
        var supply = view.supplyPlan().map(plan -> plan.demands().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> "{\"commodity\":" + string(e.getKey()) + ",\"purchase\":" + e.getValue().purchase()
                        + ",\"production\":" + e.getValue().production() + ",\"recoveryProduction\":" + e.getValue().recoveryProduction()
                        + ",\"protectedInput\":" + e.getValue().protectedInput()
                        + ",\"deadlineTicks\":" + (e.getValue().deadlineTicks() == Long.MAX_VALUE ? "null" : e.getValue().deadlineTicks())
                        + ",\"causes\":[" + e.getValue().reasons().stream().sorted().map(r -> string(r.name())).collect(Collectors.joining(",")) + "]}")
                .collect(Collectors.joining(","))).orElse("");
        var known = participant.known().stream().sorted(java.util.Comparator.comparing(value -> value.party().id()))
                .map(value -> "{\"participant\":" + string(value.party().id().value())
                        + ",\"settlement\":" + string(value.settlementId().value()) + "}").collect(Collectors.joining(","));
        var reviews = checkpoint.schedules().stream().filter(value -> value.subject().equals(participant.party().id())
                && (value.kind().equals(io.farfrontier.palemirror.frontier.v3.process.GoodsParticipantProcess.REVIEW)
                    || value.kind().equals(io.farfrontier.palemirror.frontier.v3.process.GoodsParticipantWakeup.OPPORTUNITY)))
                .mapToLong(value -> value.dueAt().ticks()).min();
        var orders = trade.orders().values().stream().filter(value -> value.party().equals(participant.party()))
                .sorted(java.util.Comparator.comparing(GoodsTradeOrder::id)).map(value ->
                        "{\"id\":" + string(value.id().value()) + ",\"side\":" + string(value.side().name())
                                + ",\"counterparty\":" + string(value.counterparty().id().value()) + ",\"commodity\":" + string(value.itemKind())
                                + ",\"container\":" + string(value.containerId().value())
                                + ",\"availableQuantity\":" + value.availableQuantity() + ",\"limitRaw\":" + value.unitPriceLimit().raw()
                                + ",\"expiresAtTick\":" + value.expiresAtTick() + "}").collect(Collectors.joining(","));
        var contracts = trade.contracts().values().stream().filter(value -> value.seller().equals(participant.party()) || value.buyer().equals(participant.party()))
                .sorted(java.util.Comparator.comparing(GoodsTradeContract::id)).map(value ->
                        "{\"id\":" + string(value.id().value()) + ",\"seller\":" + string(value.seller().id().value())
                                + ",\"buyer\":" + string(value.buyer().id().value()) + ",\"commodity\":" + string(value.itemKind())
                                + ",\"sourceContainer\":" + string(value.sourceContainerId().value())
                                + ",\"receiverContainer\":" + string(value.receiverContainerId().value())
                                + ",\"fulfillment\":" + string(value.fulfillment().mode().name())
                                + ",\"organizer\":" + string(value.fulfillment().organizer().id().value())
                                + ",\"acceptedQuantity\":" + value.acceptedQuantity() + ",\"remainingQuantity\":" + value.remainingQuantity()
                                + ",\"disposedQuantity\":" + value.disposedQuantity()
                                + ",\"withdrawalReasons\":[" + value.dispositions().values().stream()
                                    .map(disposition -> disposition.reason().name()).distinct().sorted()
                                    .map(FrontierV3GoodsParticipantDiagnosticJson::string).collect(Collectors.joining(",")) + "]"
                                + ",\"fulfilled\":" + value.fulfilled() + ",\"shipments\":["
                                + state.shipments().shipments().values().stream()
                                    .filter(shipment -> shipment.authorization().claimantId().equals(value.id()))
                                    .sorted(java.util.Comparator.comparing(Shipment::id))
                                    .map(shipment -> string(shipment.id().value())).collect(Collectors.joining(","))
                                + "]}").collect(Collectors.joining(","));
        // Acceptance is committed together with title/payment. This is a read-only
        // summary of retained contracts, not a second ledger or lifetime turnover.
        var acceptedCommodities = trade.contracts().values().stream()
                .filter(value -> value.seller().equals(participant.party()) || value.buyer().equals(participant.party()))
                .filter(value -> value.acceptedQuantity() > 0).map(GoodsTradeContract::itemKind).distinct().sorted()
                .map(kind -> string(kind) + ":true").collect(Collectors.joining(","));
        var prices = participant.prices().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> "{\"commodity\":" + string(e.getKey()) + ",\"askRaw\":" + e.getValue().ask().raw()
                        + ",\"bidRaw\":" + e.getValue().bid().raw() + ",\"referenceRaw\":" + e.getValue().reference().raw()
                        + ",\"reviewedAtTick\":" + e.getValue().reviewedAt() + "}").collect(Collectors.joining(","));
        var completedSales = trade.priceHistory().commodities().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> "{\"commodity\":" + string(e.getKey()) + ",\"acceptedQuantity\":" + e.getValue().stream()
                        .filter(s -> s.buyer().equals(participant.party().id()) || s.seller().equals(participant.party().id()))
                        .mapToLong(io.farfrontier.palemirror.frontier.v3.model.market.MarketPriceHistory.Sale::quantity).sum() + "}")
                .collect(Collectors.joining(","));
        var completedCommodities = trade.priceHistory().commodities().entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(s -> s.buyer().equals(participant.party().id())
                        || s.seller().equals(participant.party().id())))
                .sorted(java.util.Map.Entry.comparingByKey()).map(e -> string(e.getKey()) + ":true")
                .collect(Collectors.joining(","));
        return FrontierV3DiagnosticJson.base("process", participant.party().id().value(), checkpoint)
                + ",\"status\":\"ok\",\"family\":\"frontier.goods-participant\""
                + ",\"identity\":{\"participant\":" + string(participant.party().id().value()) + ",\"ownerKind\":" + string(participant.party().kind().name())
                + ",\"policy\":" + string(participant.policy().name()) + ",\"settlement\":" + string(participant.settlementId().value()) + "}"
                + ",\"declaredStorages\":[" + GoodsParticipantStorage.declared(state, participant).stream().sorted()
                    .map(id -> string(id.value())).collect(Collectors.joining(",")) + "]"
                + ",\"reviewRevision\":" + participant.reviewRevision() + ",\"reviewedAtTick\":" + participant.reviewedAtTick()
                + ",\"decision\":" + string(participant.decision()) + ",\"currentPolicy\":" + string(GoodsParticipantPolicies.require(participant)
                    .explain(view, state.bootstrap().ruleset().goodsTrade())) + ",\"availableMoneyRaw\":" + view.availableMoney().raw()
                + ",\"nextReviewTick\":" + (reviews.isPresent() ? Long.toString(reviews.orElseThrow()) : "null")
                + ",\"knownCounterparties\":[" + known + "],\"stocks\":[" + stocks
                + "],\"supplyPlan\":[" + supply + "],\"reserves\":" + view.supplyPlan().map(FrontierV3ReserveDiagnosticJson::render).orElse("[]")
                + ",\"orders\":[" + orders + "],\"contracts\":[" + contracts + "]"
                + ",\"acceptedCommodities\":{" + acceptedCommodities + "}"
                + ",\"prices\":[" + prices + "],\"completedSales\":[" + completedSales + "]"
                + ",\"completedCommodities\":{" + completedCommodities + "}"
                + ",\"expeditions\":" + FrontierV3ExpeditionDiagnosticJson.render(checkpoint, state, participant) + "}";
    }
    private static String string(String value) { return "\"" + FrontierV3DiagnosticJson.quote(value) + "\""; }
    private FrontierV3GoodsParticipantDiagnosticJson() { }
}
