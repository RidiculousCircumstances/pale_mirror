package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FrontierScheduleView;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.stream.Collectors;

/** Read-only public/company policy, stock and obligation join. It grants no consent or title. */
final class FrontierV3GoodsParticipantDiagnosticJson {
    static String render(FrontierScheduleView checkpoint, FrontierWorldState state, GoodsParticipant participant) {
        var view = GoodsParticipantView.read(state, participant);
        var stocks = view.stocks().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).map(entry ->
                "{\"commodity\":" + string(entry.getKey()) + ",\"owned\":" + entry.getValue().ownedAtEndpoint()
                        + ",\"unclaimed\":" + entry.getValue().unclaimedAtEndpoint() + ",\"incoming\":" + entry.getValue().expectedIncoming()
                        + ",\"protectedMinimum\":" + entry.getValue().protectedMinimum() + "}").collect(Collectors.joining(","));
        var trade = state.companies().goodsTrade();
        var known = participant.known().stream().sorted(java.util.Comparator.comparing(value -> value.party().id()))
                .map(value -> "{\"participant\":" + string(value.party().id().value())
                        + ",\"container\":" + string(value.endpoint().containerId().value()) + "}").collect(Collectors.joining(","));
        var reviews = checkpoint.schedules().stream().filter(value -> value.subject().equals(participant.party().id())
                && (value.kind().equals(io.farfrontier.palemirror.frontier.v3.process.GoodsParticipantProcess.REVIEW)
                    || value.kind().equals(io.farfrontier.palemirror.frontier.v3.process.GoodsParticipantWakeup.OPPORTUNITY)))
                .mapToLong(value -> value.dueAt().ticks()).min();
        var orders = trade.orders().values().stream().filter(value -> value.party().equals(participant.party()))
                .sorted(java.util.Comparator.comparing(GoodsTradeOrder::id)).map(value ->
                        "{\"id\":" + string(value.id().value()) + ",\"side\":" + string(value.side().name())
                                + ",\"counterparty\":" + string(value.counterparty().id().value()) + ",\"commodity\":" + string(value.itemKind())
                                + ",\"availableQuantity\":" + value.availableQuantity() + ",\"limitRaw\":" + value.unitPriceLimit().raw()
                                + ",\"expiresAtTick\":" + value.expiresAtTick() + "}").collect(Collectors.joining(","));
        var contracts = trade.contracts().values().stream().filter(value -> value.seller().equals(participant.party()) || value.buyer().equals(participant.party()))
                .sorted(java.util.Comparator.comparing(GoodsTradeContract::id)).map(value ->
                        "{\"id\":" + string(value.id().value()) + ",\"seller\":" + string(value.seller().id().value())
                                + ",\"buyer\":" + string(value.buyer().id().value()) + ",\"commodity\":" + string(value.itemKind())
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
        return FrontierV3DiagnosticJson.base("process", participant.party().id().value(), checkpoint)
                + ",\"status\":\"ok\",\"family\":\"frontier.goods-participant\""
                + ",\"identity\":{\"participant\":" + string(participant.party().id().value()) + ",\"ownerKind\":" + string(participant.party().kind().name())
                + ",\"policy\":" + string(participant.policy().name()) + ",\"container\":" + string(participant.endpoint().containerId().value()) + "}"
                + ",\"reviewRevision\":" + participant.reviewRevision() + ",\"reviewedAtTick\":" + participant.reviewedAtTick()
                + ",\"decision\":" + string(participant.decision()) + ",\"currentPolicy\":" + string(GoodsParticipantPolicies.require(participant)
                    .explain(view, state.bootstrap().ruleset().goodsTrade())) + ",\"availableMoneyRaw\":" + view.availableMoney().raw()
                + ",\"nextReviewTick\":" + (reviews.isPresent() ? Long.toString(reviews.orElseThrow()) : "null")
                + ",\"knownCounterparties\":[" + known + "],\"stocks\":[" + stocks
                + "],\"orders\":[" + orders + "],\"contracts\":[" + contracts + "]"
                + ",\"expeditions\":" + FrontierV3ExpeditionDiagnosticJson.render(checkpoint, state, participant) + "}";
    }
    private static String string(String value) { return "\"" + FrontierV3DiagnosticJson.quote(value) + "\""; }
    private FrontierV3GoodsParticipantDiagnosticJson() { }
}
