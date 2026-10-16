package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.math.BigInteger;
import java.util.*;

/** Immutable external facts. Offers are alternatives, never incoming inventory or reserved money. */
public record SourcingMarket(long availableMoney, int switchingSavingsPermille,
                             Map<String, Long> processUnitCosts, List<Offer> offers) {
    public record Offer(String id, String item, long quantity, long cashPerItem, long deliveredCostPerItem,
                        long deliveryTicks, long tripCost) {
        public Offer {
            if (id == null || id.isBlank() || item == null || item.isBlank() || quantity < 1 || cashPerItem < 1
                    || deliveredCostPerItem < cashPerItem || deliveryTicks < 0 || tripCost < 0)
                throw new IllegalArgumentException("invalid priced supply offer");
        }
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
        private final Map<String, Long> spending = new TreeMap<>();
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
                long take = Math.min(quantity - bought, Math.min(offer.quantity() - used.getOrDefault(offer.id(), 0L),
                        remaining / offer.cashPerItem()));
                if (take == 0 || !cheaper(Math.addExact(offer.deliveredCostPerItem(), Math.ceilDiv(offer.tripCost(), take)), ownCost)) continue;
                long cost = Math.multiplyExact(take, offer.cashPerItem());
                used.merge(offer.id(), take, Math::addExact); spending.merge(item, cost, Math::addExact);
                remaining -= cost; bought += take;
                if (bought == quantity) break;
            }
            return bought;
        }
        private boolean cheaper(long external, long local) {
            return local == Long.MAX_VALUE || BigInteger.valueOf(external).multiply(BigInteger.valueOf(1000))
                    .compareTo(BigInteger.valueOf(local).multiply(BigInteger.valueOf(1000L - market.switchingSavingsPermille()))) < 0;
        }
        private long effectiveCost(Offer offer, long quantity) {
            long take = Math.min(quantity, Math.min(offer.quantity() - used.getOrDefault(offer.id(), 0L), remaining / offer.cashPerItem()));
            return take < 1 ? Long.MAX_VALUE : Math.addExact(offer.deliveredCostPerItem(), Math.ceilDiv(offer.tripCost(), take));
        }
        public Map<String, Long> spending() { return Map.copyOf(spending); }
    }
}
