package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.math.BigInteger;
import java.util.*;

/** Immutable external facts. Offers are alternatives, never incoming inventory or reserved money. */
public record SourcingMarket(long availableMoney, int switchingSavingsPermille,
                             Map<String, Long> processUnitCosts, List<Offer> offers) {
    public record Offer(String id, String item, long quantity, long cashPerItem, long deliveredCostPerItem,
                        long deliveryTicks, LotTransportCost transport) {
        public Offer(String id, String item, long quantity, long cashPerItem, long deliveredCostPerItem, long deliveryTicks, long tripCost, long tripCashCost) {
            this(id, item, quantity, cashPerItem, deliveredCostPerItem, deliveryTicks, LotTransportCost.fixed(quantity, tripCost, tripCashCost));
        }
        public Offer(String id, String item, long quantity, long cashPerItem, long deliveredCostPerItem, long deliveryTicks, long tripCost) {
            this(id, item, quantity, cashPerItem, deliveredCostPerItem, deliveryTicks, tripCost, 0);
        }
        public Offer {
            if (id == null || id.isBlank() || item == null || item.isBlank() || quantity < 1 || cashPerItem < 1
                    || deliveredCostPerItem < cashPerItem || deliveryTicks < 0 || transport == null)
                throw new IllegalArgumentException("invalid priced supply offer");
            transport.quote(quantity);
        }
        public long tripCost() { return transport.quote(quantity).economic(); }
        public long tripCashCost() { return transport.quote(quantity).cash(); }
    }
    public SourcingMarket {
        processUnitCosts = Map.copyOf(processUnitCosts); offers = List.copyOf(offers);
        if (availableMoney < 0 || switchingSavingsPermille < 0 || switchingSavingsPermille >= 1000
                || offers.size() > 4096 || processUnitCosts.size() > 256
                || processUnitCosts.values().stream().anyMatch(cost -> cost < 0)
                || offers.stream().map(Offer::id).distinct().count() != offers.size())
            throw new IllegalArgumentException("invalid bounded sourcing market");
    }
    public static SourcingMarket none() { return new SourcingMarket(0, 0, Map.of(), List.of()); }
    public Allocation allocate() { return new Allocation(this); }

    /** One planning invocation shares the budget and offer capacity across every dependency. */
    public static final class Allocation {
        private final SourcingMarket market;
        private final Map<String, Long> used = new HashMap<>();
        private final Map<String, LotTransportCost.Cost> transport = new HashMap<>();
        private final Map<String, Long> spending = new TreeMap<>();
        private final Map<String, Long> purchased = new TreeMap<>();
        private long remaining;
        private Allocation(SourcingMarket market) { this.market = market; remaining = market.availableMoney(); }
        public long processCost(SupplyPlanner.Process process) {
            return market.processUnitCosts().getOrDefault(process.id(), Long.MAX_VALUE);
        }
        public long preferPurchase(String item, long quantity, long deadline, Optional<SupplyPlanner.Process> process) {
            if (process.isPresent() && !market.processUnitCosts().containsKey(process.orElseThrow().id())) return 0;
            long ownCost = process.map(this::processCost).orElse(Long.MAX_VALUE);
            long bought = 0;
            for (var offer : market.offers().stream().filter(value -> value.item().equals(item))
                    .sorted(Comparator.<Offer>comparingLong(value -> effectiveCost(value, quantity)).thenComparing(Offer::id)).toList()) {
                if (offer.deliveryTicks() > deadline) continue;
                var allocation = funded(offer, quantity - bought);
                if (allocation.isEmpty()) continue;
                var purchase = allocation.orElseThrow(); long take = purchase.added();
                var priorCost = transport.getOrDefault(offer.id(), new LotTransportCost.Cost(0, 0));
                if (!cheaper(Math.addExact(offer.deliveredCostPerItem(), Math.ceilDiv(
                        purchase.totalTransport().economic() - priorCost.economic(), take)), ownCost)) continue;
                long cost = Math.addExact(Math.multiplyExact(take, offer.cashPerItem()), purchase.totalTransport().cash() - priorCost.cash());
                transport.put(offer.id(), purchase.totalTransport());
                used.merge(offer.id(), take, Math::addExact); spending.merge(item, cost, Math::addExact);
                purchased.merge(item, take, Math::addExact);
                remaining -= cost; bought += take;
                if (bought == quantity) break;
            }
            return bought;
        }
        private boolean cheaper(long external, long local) {
            return local == Long.MAX_VALUE || BigInteger.valueOf(external).multiply(BigInteger.valueOf(1000))
                    .compareTo(BigInteger.valueOf(local).multiply(BigInteger.valueOf(1000L - market.switchingSavingsPermille()))) < 0;
        }
        private Optional<LotTransportCost.Purchase> funded(Offer offer, long quantity) {
            long prior = used.getOrDefault(offer.id(), 0L);
            return offer.transport().funded(prior, transport.getOrDefault(offer.id(), new LotTransportCost.Cost(0, 0)),
                    Math.min(quantity, offer.quantity() - prior), offer.cashPerItem(), remaining);
        }
        private long effectiveCost(Offer offer, long quantity) {
            var purchase = funded(offer, quantity);
            if (purchase.isEmpty()) return Long.MAX_VALUE;
            var prior = transport.getOrDefault(offer.id(), new LotTransportCost.Cost(0, 0));
            return Math.addExact(offer.deliveredCostPerItem(), Math.ceilDiv(
                    purchase.orElseThrow().totalTransport().economic() - prior.economic(), purchase.orElseThrow().added()));
        }
        public Map<String, Long> spending() { return Map.copyOf(spending); }
        public Map<String, Long> purchased() { return Map.copyOf(purchased); }
    }
}
