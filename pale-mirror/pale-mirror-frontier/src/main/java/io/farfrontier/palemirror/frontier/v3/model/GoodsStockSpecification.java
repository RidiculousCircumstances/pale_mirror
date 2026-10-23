package io.farfrontier.palemirror.frontier.v3.model;

/** Market quality contract, independent of tool capabilities and concrete item names. */
public record GoodsStockSpecification(Representation representation, int minimumRemainingPermille) {
    public enum Representation { FUNGIBLE, INDIVIDUAL }
    public static final GoodsStockSpecification FUNGIBLE = new GoodsStockSpecification(Representation.FUNGIBLE, 0);
    public GoodsStockSpecification {
        if (representation == null || minimumRemainingPermille < 0 || minimumRemainingPermille > 1000
                || representation == Representation.FUNGIBLE && minimumRemainingPermille != 0)
            throw new IllegalArgumentException("invalid commodity stock/quality declaration");
    }
    public boolean accepts(ExactItemStack item) {
        return representation == Representation.INDIVIDUAL && item != null && item.count() == 1
                && item.condition().durable() && (long) item.condition().remaining() * 1000
                    >= (long) minimumRemainingPermille * item.condition().maximumDamage();
    }
    public static GoodsStockSpecification forKind(GoodsTradeRules rules, String kind) {
        return rules.policies().values().stream().flatMap(java.util.Collection::stream)
                .filter(c -> c.itemKind().equals(kind)).map(GoodsTradeRules.Commodity::stock)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("undeclared market commodity: " + kind));
    }
}
