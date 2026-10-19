package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Bounded time-bucketed funded interest. Missing buckets contribute zero demand;
 * refresh frequency cannot multiply demand. A quote is never a sale or receipt. */
public record MarketDemandHistory(Map<Key, Observation> observations) {
    public static final int MAX_ENTRIES = 1024;
    public static final int WINDOW_BUCKETS = 32;
    public record Key(SubjectId buyer, String item) {
        public Key { Objects.requireNonNull(buyer); if (item == null || item.isBlank()) throw new IllegalArgumentException("missing demand item"); }
    }
    public record Quote(long quantity, long unitPrice) {
        public Quote { if (quantity < 1 || unitPrice < 1) throw new IllegalArgumentException("unfunded demand quote"); }
    }
    public record Sample(long bucket, long quantity, long price) {
        public Sample {
            if (bucket < 0 || quantity < 0 || price < 0 || (quantity == 0) != (price == 0))
                throw new IllegalArgumentException("invalid demand sample");
        }
    }
    public record Observation(long firstTick, long lastTick, long bucketTicks, List<Sample> samples) {
        public Observation {
            samples = List.copyOf(samples);
            if (firstTick < 0 || lastTick < firstTick || bucketTicks < 1 || samples.isEmpty()
                    || samples.size() > WINDOW_BUCKETS + 1)
                throw new IllegalArgumentException("invalid rolling market observation");
            long previous = -1;
            for (var sample : samples) {
                if (sample.bucket() <= previous || sample.bucket() > lastTick / bucketTicks)
                    throw new IllegalArgumentException("unordered or future demand sample");
                previous = sample.bucket();
            }
        }
        public Optional<Quote> sustainedQuote(long now, long duration, long maximumGap) {
            if (duration < 1 || maximumGap < 1 || bucketTicks != Math.max(1, Math.ceilDiv(duration, WINDOW_BUCKETS)))
                throw new IllegalArgumentException("inconsistent demand window");
            if (now < lastTick || now - lastTick > maximumGap || now - firstTick < duration) return Optional.empty();
            long first = Math.floorDiv(now - duration, bucketTicks), last = now / bucketTicks;
            long quantity = 0, price = 0, priced = 0;
            for (var sample : samples) if (sample.bucket() >= first && sample.bucket() <= last) {
                quantity = Math.addExact(quantity, sample.quantity());
                if (sample.quantity() > 0) { price = Math.addExact(price, sample.price()); priced++; }
            }
            // Empty/missing buckets remain in the denominator. A momentary filled
            // order reduces the estimate instead of erasing two days of evidence.
            quantity /= last - first + 1;
            return quantity == 0 ? Optional.empty() : Optional.of(new Quote(quantity, price / priced));
        }
    }
    public MarketDemandHistory {
        observations = Map.copyOf(observations);
        if (observations.size() > MAX_ENTRIES) throw new IllegalArgumentException("market demand register exceeds its bound");
    }
    public static MarketDemandHistory empty() { return new MarketDemandHistory(Map.of()); }
    public MarketDemandHistory observe(SubjectId buyer, long now, long windowTicks, Map<String, Quote> funded) {
        if (now < 0 || windowTicks < 1) throw new IllegalArgumentException("invalid market observation clock");
        var next = new HashMap<Key, Observation>(observations);
        next.entrySet().removeIf(entry -> now - entry.getValue().lastTick() > windowTicks);
        var items = new TreeSet<>(funded.keySet());
        next.keySet().stream().filter(key -> key.buyer().equals(buyer)).forEach(key -> items.add(key.item()));
        long width = Math.max(1, Math.ceilDiv(windowTicks, WINDOW_BUCKETS)), bucket = now / width;
        long oldest = Math.floorDiv(Math.max(0, now - windowTicks), width);
        for (var item : items) {
            var key = new Key(buyer, item); var quote = funded.get(item); var prior = next.get(key);
            if (prior != null && now < prior.lastTick()) throw new IllegalArgumentException("market observation clock moved backwards");
            if (prior != null && prior.bucketTicks() != width) throw new IllegalArgumentException("market window changed without a rules transition");
            var samples = new ArrayList<Sample>();
            if (prior != null) prior.samples().stream().filter(s -> s.bucket() >= oldest && s.bucket() < bucket).forEach(samples::add);
            samples.add(new Sample(bucket, quote == null ? 0 : quote.quantity(), quote == null ? 0 : quote.unitPrice()));
            if (samples.stream().noneMatch(s -> s.quantity() > 0)) next.remove(key);
            else next.put(key, new Observation(prior == null ? now : prior.firstTick(), now, width, samples));
        }
        return new MarketDemandHistory(next);
    }
}
