package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.math.BigInteger;
import java.util.*;

/** Buyer policy compares complete feasible offers. Calculation never reserves or moves anything. */
public final class ProcurementSelection {
    public record Requirement(String item, int quantity, long deadlineHorizonTicks) {
        public Requirement {
            Objects.requireNonNull(item);
            if (item.isBlank() || quantity < 1 || deadlineHorizonTicks < 0) throw new IllegalArgumentException("invalid purchase requirement");
        }
    }
    public record Line(int quantity, FixedScalar unitPrice) {
        public Line {
            Objects.requireNonNull(unitPrice);
            if (quantity < 1 || unitPrice.raw() <= 0) throw new IllegalArgumentException("invalid offered goods line");
            unitPrice.multiply(quantity);
        }
    }
    /** Transport cost is an explicit monetary estimate, distinct from goods payment and crew time. */
    public record Offer(SubjectId id, Map<String, Line> goods, FixedScalar transportCost,
                        long arrivalDelayTicks, long ownWorkerTicks) {
        public Offer {
            Objects.requireNonNull(id); goods = Map.copyOf(goods); Objects.requireNonNull(transportCost);
            if (goods.isEmpty() || goods.size() > 256 || transportCost.raw() < 0 || arrivalDelayTicks < 0 || ownWorkerTicks < 0)
                throw new IllegalArgumentException("invalid feasible procurement offer");
            long total = transportCost.raw();
            for (var line : goods.values()) total = Math.addExact(total, line.unitPrice().multiply(line.quantity()).raw());
        }
    }

    /** Earliest need first; among offers for it compare delivered cost, then own labour and arrival.
     * Fixed trip overhead is allocated by quoted goods value, preserving the whole trip cost.
     * This is bounded greedy procurement, not a claim of optimal multi-trip packing. */
    public Optional<SubjectId> select(List<Requirement> requirements, List<Offer> offers) {
        if (requirements.size() > 256 || offers.size() > 2048
                || requirements.stream().map(Requirement::item).distinct().count() != requirements.size()
                || offers.stream().map(Offer::id).distinct().count() != offers.size())
            throw new IllegalArgumentException("unbounded or duplicate procurement alternatives");
        for (var need : requirements.stream().sorted(Comparator.comparingLong(Requirement::deadlineHorizonTicks)
                .thenComparing(Requirement::item)).toList()) {
            var candidates = offers.stream().filter(o -> o.goods().containsKey(need.item())).toList();
            if (candidates.isEmpty()) continue;
            // When all offers miss the deadline, earliest recovery remains useful. Never
            // pretend no demand exists merely because timely provision has become impossible.
            boolean timely = candidates.stream().anyMatch(o -> o.arrivalDelayTicks() <= need.deadlineHorizonTicks());
            return candidates.stream().filter(o -> !timely || o.arrivalDelayTicks() <= need.deadlineHorizonTicks())
                    .min((a, b) -> compare(a, b, need, timely)).map(Offer::id);
        }
        return Optional.empty();
    }
    private static int compare(Offer a, Offer b, Requirement need, boolean timely) {
        if (!timely) {
            int arrival = Long.compare(a.arrivalDelayTicks(), b.arrivalDelayTicks());
            if (arrival != 0) return arrival;
        }
        var av = goodsValue(a); var bv = goodsValue(b);
        var ac = av.add(BigInteger.valueOf(a.transportCost().raw()));
        var bc = bv.add(BigInteger.valueOf(b.transportCost().raw()));
        int price = BigInteger.valueOf(a.goods().get(need.item()).unitPrice().raw()).multiply(ac).multiply(bv)
                .compareTo(BigInteger.valueOf(b.goods().get(need.item()).unitPrice().raw()).multiply(bc).multiply(av));
        if (price != 0) return price;
        int labour = Long.compare(a.ownWorkerTicks(), b.ownWorkerTicks());
        if (labour != 0) return labour;
        int arrival = Long.compare(a.arrivalDelayTicks(), b.arrivalDelayTicks());
        if (arrival != 0) return arrival;
        int volume = Integer.compare(Math.min(need.quantity(), b.goods().get(need.item()).quantity()),
                Math.min(need.quantity(), a.goods().get(need.item()).quantity()));
        return volume != 0 ? volume : a.id().compareTo(b.id());
    }
    private static BigInteger goodsValue(Offer offer) {
        return offer.goods().values().stream().map(l -> BigInteger.valueOf(l.unitPrice().multiply(l.quantity()).raw()))
                .reduce(BigInteger.ZERO, BigInteger::add);
    }
}
