package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.*;

/** Persisted balance choices. Commodity names belong here, not to the market or carrier. */
public record GoodsTradeRules(long reviewInterval, long orderLifetime, int maximumBatch, int maximumPairReviews,
                             Map<GoodsPolicyKind, List<Commodity>> policies) {
    public record Commodity(String itemKind, int targetPerResident, int targetFixed,
                            FixedScalar minimumSellPrice, FixedScalar maximumBuyPrice) {
        public Commodity {
            Objects.requireNonNull(minimumSellPrice); Objects.requireNonNull(maximumBuyPrice);
            if (itemKind == null || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")
                    || targetPerResident < 0 || targetPerResident > 64 || targetFixed < 0 || targetFixed > 4096
                    || minimumSellPrice.raw() <= 0 || maximumBuyPrice.raw() <= 0)
                throw new IllegalArgumentException("invalid goods commodity policy");
        }
        public int target(int residents) { return Math.addExact(targetFixed, Math.multiplyExact(targetPerResident, residents)); }
    }
    public GoodsTradeRules {
        var copy = new EnumMap<GoodsPolicyKind, List<Commodity>>(GoodsPolicyKind.class);
        policies.forEach((kind, commodities) -> {
            var values = List.copyOf(commodities);
            if (values.isEmpty() || values.size() > 16
                    || values.stream().map(Commodity::itemKind).distinct().count() != values.size())
                throw new IllegalArgumentException("goods policy must declare a bounded unique commodity catalog");
            copy.put(Objects.requireNonNull(kind), values);
        });
        policies = Map.copyOf(copy);
        if (reviewInterval < 1 || orderLifetime < reviewInterval || maximumBatch < 1 || maximumBatch > 64
                || maximumPairReviews < 1 || maximumPairReviews > 2048
                || policies.size() != GoodsPolicyKind.values().length)
            throw new IllegalArgumentException("incomplete or invalid goods policy rules");
    }
    public static GoodsTradeRules standard() {
        return new GoodsTradeRules(400, 24000, 64, 64, Map.of(
                GoodsPolicyKind.PUBLIC_SETTLEMENT, List.of(
                        new Commodity("minecraft:bread", 4, 0, FixedScalar.whole(2), FixedScalar.whole(3)),
                        new Commodity("minecraft:wheat", 0, 64, FixedScalar.ONE, FixedScalar.ONE)),
                GoodsPolicyKind.OWN_ACCOUNT_COMPANY, List.of(
                        new Commodity("minecraft:wheat", 0, 64, FixedScalar.ONE, FixedScalar.ONE),
                        new Commodity("minecraft:bread", 0, 0, FixedScalar.whole(2), FixedScalar.whole(2)))));
    }
    public String canonicalText() {
        var result = new StringBuilder(reviewInterval + "," + orderLifetime + "," + maximumBatch + "," + maximumPairReviews);
        policies.entrySet().stream().sorted(Comparator.comparingInt(e -> e.getKey().wireTag())).forEach(entry -> {
            result.append('|').append(entry.getKey().wireTag());
            entry.getValue().stream().sorted(Comparator.comparing(Commodity::itemKind)).forEach(value ->
                    result.append(';').append(value.itemKind()).append(',').append(value.targetPerResident())
                            .append(',').append(value.targetFixed()).append(',').append(value.minimumSellPrice().raw())
                            .append(',').append(value.maximumBuyPrice().raw()));
        });
        return result.toString();
    }
}
