package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Bounded continuous funded-interest observations. A quote is never a sale or incoming supply. */
public record MarketDemandHistory(Map<Key, Observation> observations) {
    public static final int MAX_ENTRIES = 1024;
    public record Key(SubjectId buyer, String item) {
        public Key { Objects.requireNonNull(buyer); if (item == null || item.isBlank()) throw new IllegalArgumentException("missing demand item"); }
    }
    public record Quote(long quantity, long unitPrice) {
        public Quote { if (quantity < 1 || unitPrice < 1) throw new IllegalArgumentException("unfunded demand quote"); }
    }
    public record Observation(long firstTick, long lastTick, long minimumQuantity, long minimumPrice) {
        public Observation {
            if (firstTick < 0 || lastTick < firstTick || minimumQuantity < 1 || minimumPrice < 1)
                throw new IllegalArgumentException("invalid continuous market observation");
        }
        public boolean sustained(long now, long duration, long maximumGap) {
            return now >= lastTick && now - lastTick <= maximumGap && lastTick - firstTick >= duration;
        }
    }
    public MarketDemandHistory {
        observations = Map.copyOf(observations);
        if (observations.size() > MAX_ENTRIES) throw new IllegalArgumentException("market demand register exceeds its bound");
    }
    public static MarketDemandHistory empty() { return new MarketDemandHistory(Map.of()); }
    public MarketDemandHistory observe(SubjectId buyer, long now, long maximumGap, Map<String, Quote> funded) {
        if (now < 0 || maximumGap < 1) throw new IllegalArgumentException("invalid market observation clock");
        var next = new HashMap<Key, Observation>(observations);
        next.entrySet().removeIf(entry -> now - entry.getValue().lastTick() > maximumGap
                || entry.getKey().buyer().equals(buyer) && !funded.containsKey(entry.getKey().item()));
        for (var entry : funded.entrySet()) {
            var key = new Key(buyer, entry.getKey()); var quote = entry.getValue(); var prior = next.get(key);
            if (prior != null && now < prior.lastTick()) throw new IllegalArgumentException("market observation clock moved backwards");
            next.put(key, prior == null ? new Observation(now, now, quote.quantity(), quote.unitPrice())
                    : new Observation(prior.firstTick(), now, Math.min(prior.minimumQuantity(), quote.quantity()),
                            Math.min(prior.minimumPrice(), quote.unitPrice())));
        }
        return new MarketDemandHistory(next);
    }
}
