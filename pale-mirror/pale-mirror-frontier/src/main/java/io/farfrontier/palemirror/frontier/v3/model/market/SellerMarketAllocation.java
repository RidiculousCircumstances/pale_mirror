package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** Read-only demand allocation using actual buyer utility/timing selection.
 * Never reserves a quote, spends cash or claims a future sale. */
final class SellerMarketAllocation {
    private static final SubjectId OWN = new SubjectId("pricing:offered-lot");
    private record Funded(ProcurementSelection.Offer offer, int quantity, long invoice) { }
    static long sold(long ask, long surplus, SellerPriceDiscovery.Market market) {
        long sold = 0;
        var used = new HashMap<SubjectId, Integer>();
        var choice = new ProcurementSelection();
        for (var buyer : market.buyers()) {
            if (sold >= surplus) break;
            int remaining = buyer.quantity();
            long budget = buyer.budget().raw();
            var sources = new ArrayList<>(buyer.alternatives());
            sources.add(new SellerPriceDiscovery.Offer(OWN, Math.toIntExact(Math.min(4096, surplus - sold)), new FixedScalar(ask),
                    buyer.fixedDelivery(), buyer.cashDelivery(), buyer.arrivalDelayTicks(), buyer.workerTicks(),
                    buyer.minimumQuantity(), buyer.departure()));
            while (remaining > 0 && !sources.isEmpty() && budget > 0) {
                var funded = new TreeMap<SubjectId, Funded>();
                for (var source : sources) {
                    int available = source.quantity() - (source.seller().equals(OWN) ? 0 : used.getOrDefault(source.seller(), 0));
                    long variable = source.seller().equals(OWN) ? buyer.deliveryPerItem().raw() : 0;
                    int quantity = FundedCargoLot.quantity(Math.min(remaining, Math.max(0, available)),
                            Math.addExact(source.goodsPrice().raw(), variable), source.cashFreight().raw(), budget);
                    if (quantity < source.minimumQuantity()) continue;
                    long freight = Math.addExact(source.resourceFreight().raw(), Math.multiplyExact(variable, quantity));
                    if (source.goodsPrice().raw() + Math.ceilDiv(freight, quantity) > buyer.landedLimit().raw()) continue;
                    var offer = new ProcurementSelection.Offer(source.seller(),
                            Map.of("quoted-item", new ProcurementSelection.Line(quantity, source.goodsPrice())),
                            new FixedScalar(freight), source.arrivalDelayTicks(), source.workerTicks(), source.departure());
                    long invoice = Math.addExact(Math.multiplyExact(quantity, Math.addExact(source.goodsPrice().raw(), variable)), source.cashFreight().raw());
                    funded.put(source.seller(), new Funded(offer, quantity, invoice));
                }
                var selected = choice.select(List.of(new ProcurementSelection.Requirement("quoted-item", remaining, buyer.deadlineTicks())),
                        funded.values().stream().map(Funded::offer).toList(), buyer.labourValuePerTick());
                if (selected.isEmpty()) break;
                var id = selected.orElseThrow();
                var value = funded.get(id);
                remaining -= value.quantity();
                budget -= value.invoice();
                if (id.equals(OWN)) sold += value.quantity(); else used.merge(id, value.quantity(), Math::addExact);
                sources.removeIf(source -> source.seller().equals(id));
            }
        }
        return sold;
    }
    private SellerMarketAllocation() { }
}
