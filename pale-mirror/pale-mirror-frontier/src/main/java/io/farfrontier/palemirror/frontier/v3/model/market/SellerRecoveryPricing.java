package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.*;

/** Minimum economical stock lot financing a complete declared operating shortfall.
 * Never production authority, an assumed receipt or a pennies-only liquidation. */
final class SellerRecoveryPricing {
    record Choice(FixedScalar price, long quantity) { }
    static Optional<Choice> select(FixedScalar ordinary, long surplus, SellerPriceDiscovery.Market market, long cash) {
        if (cash < 1 || surplus < 1 || market.buyers().isEmpty()
                || SellerPriceDiscovery.sold(ordinary.raw(), surplus, market) >= Math.ceilDiv(cash, ordinary.raw())) return Optional.empty();
        Choice best = null;
        for (long price : SellerPriceDiscovery.candidates(ordinary.raw(), 1, surplus, market).reversed()) {
            if (price >= ordinary.raw()) continue;
            long needed = Math.ceilDiv(cash, price);
            if (needed > surplus || best != null && needed > best.quantity()) continue;
            for (long quantity : lots(price, needed, surplus, market)) {
                if (best != null && quantity > best.quantity()) break;
                if (SellerPriceDiscovery.sold(price, quantity, market) < needed) continue;
                if (best == null || quantity < best.quantity() || quantity == best.quantity() && price > best.price().raw())
                    best = new Choice(new FixedScalar(price), quantity);
                break;
            }
        }
        return Optional.ofNullable(best);
    }
    private static SortedSet<Long> lots(long price, long needed, long surplus, SellerPriceDiscovery.Market market) {
        var result = new TreeSet<Long>(); result.add(needed); long cumulative = 0;
        for (var buyer : market.buyers()) {
            cumulative = Math.min(surplus, Math.addExact(cumulative, buyer.quantity()));
            result.add(Math.min(surplus, Math.max(needed, cumulative)));
            result.add(Math.min(surplus, Math.max(needed, buyer.quantity())));
            long room = buyer.landedLimit().raw() - price - buyer.deliveryPerItem().raw();
            if (room > 0) result.add(Math.min(surplus, Math.max(needed, Math.max(buyer.minimumQuantity(), Math.ceilDiv(buyer.fixedDelivery().raw(), room)))));
            for (var other : buyer.alternatives()) {
                long competitive = other.landedPrice().raw() - price - buyer.deliveryPerItem().raw();
                if (competitive > 0) result.add(Math.min(surplus, Math.max(needed, Math.ceilDiv(buyer.fixedDelivery().raw(), competitive))));
                if (other.arrivalDelayTicks() > 0) result.add(Math.min(surplus, Math.max(needed,
                        Math.ceilDiv(Math.multiplyExact((long) other.quantity(), Math.max(1, buyer.arrivalDelayTicks())), other.arrivalDelayTicks()))));
            }
        }
        return result;
    }
    private SellerRecoveryPricing() { }
}
