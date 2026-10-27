package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.market.ProcurementSelection;
import java.util.*;

/** Disposable quote envelope, never a claim on stock, money, capacity or workers.
 * Exact admission may reduce or reject it and must return to the same ranking. */
final class GoodsTripQuotes {
    static Map<String, ProcurementSelection.Line> lines(FrontierWorldState state, GoodsParticipant seller,
            GoodsParticipant buyer, GoodsOrderMatching.Endpoints endpoints, long now) {
        var quotes = GoodsOrderMatching.quotes(state, seller, buyer, now).stream()
                .filter(pair -> pair.sell().containerId().equals(endpoints.source())
                        && pair.buy().containerId().equals(endpoints.receiver())
                        && pair.sell().unitPriceLimit().compareTo(pair.buy().unitPriceLimit()) <= 0).toList();
        var remainders = new HashMap<SubjectId, Integer>();
        var quantities = new TreeMap<String, Integer>(); var amounts = new TreeMap<String, Long>();
        int slots = Math.min(state.bootstrap().ruleset().goodsTrade().supplyPlanning().maximumTripCargoStacks(),
                ShipmentState.MAX_SHIPMENTS - state.shipments().shipments().size());
        var considered = new HashSet<String>();
        while (slots > 0) {
            boolean added = false;
            for (var pair : quotes) {
                var sell = pair.sell(); var buy = pair.buy();
                if (considered.contains(sell.itemKind())) continue;
                int quantity = Math.min(GoodsTransportOptions.parcelSize(state, sell.itemKind()),
                        Math.min(remainders.getOrDefault(sell.id(), sell.availableQuantity()),
                                remainders.getOrDefault(buy.id(), buy.availableQuantity())));
                if (quantity == 0) continue;
                quantities.merge(sell.itemKind(), quantity, Math::addExact);
                amounts.merge(sell.itemKind(), sell.unitPriceLimit().multiply(quantity).raw(), Math::addExact);
                remainders.put(sell.id(), remainders.getOrDefault(sell.id(), sell.availableQuantity()) - quantity);
                remainders.put(buy.id(), remainders.getOrDefault(buy.id(), buy.availableQuantity()) - quantity);
                considered.add(sell.itemKind()); slots--; added = true; break;
            }
            if (!added) {
                if (considered.isEmpty()) break;
                considered.clear();
            }
        }
        var result = new TreeMap<String, ProcurementSelection.Line>();
        quantities.forEach((item, quantity) -> result.put(item, new ProcurementSelection.Line(quantity,
                new FixedScalar(Math.ceilDiv(amounts.get(item), quantity)))));
        return Map.copyOf(result);
    }
    private GoodsTripQuotes() { }
}
