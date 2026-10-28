package io.farfrontier.palemirror.frontier.v3.model;

/** Market quality contract, independent of tool capabilities and concrete item names. */
public record GoodsStockSpecification(Representation representation, int minimumRemainingPermille) {
    private static final ImmutableInputView<java.util.Map<String, GoodsStockSpecification>> CATALOG = new ImmutableInputView<>();
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
        // The immutable rules constructor already requires agreement between
        // policies. Index declarations once; stock/funding/quality checks stay
        // with their current owners and never enter this catalog.
        var catalog = CATALOG.get(java.util.List.of(rules.policies()), () -> {
            var result = new java.util.HashMap<String, GoodsStockSpecification>();
            for (var commodities : rules.policies().values())
                for (var commodity : commodities) result.put(commodity.itemKind(), commodity.stock());
            return java.util.Map.copyOf(result);
        });
        var specification = catalog.get(kind);
        if (specification == null) throw new IllegalArgumentException("undeclared market commodity: " + kind);
        return specification;
    }
}
