package io.farfrontier.palemirror.frontier.v3.model;

/** Fresh authored footprints; ongoing field work still consumes arbitrary explicit cell sets. */
public record FieldBalance(int ordinarySide, int extractiveSide,
                           java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.agriculture.TerritoryAgriculture> agriculture,
                           java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.agriculture.CultivationRules> cultivation) {
    public FieldBalance(int ordinarySide, int extractiveSide,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.agriculture.TerritoryAgriculture> agriculture) {
        this(ordinarySide, extractiveSide, agriculture, java.util.Optional.empty());
    }
    public FieldBalance(int ordinarySide, int extractiveSide) { this(ordinarySide, extractiveSide, java.util.Optional.empty()); }
    public FieldBalance {
        java.util.Objects.requireNonNull(agriculture);
        java.util.Objects.requireNonNull(cultivation);
        if ((ordinarySide != 8 && ordinarySide != 16) || (extractiveSide != 8 && extractiveSide != 16))
            throw new IllegalArgumentException("unsupported authored initial field footprint");
    }
    public static FieldBalance uniform() { return new FieldBalance(16, 16); }
    public String canonicalText() { return ordinarySide + ":" + extractiveSide
            + agriculture.map(value -> "|agriculture=" + value.canonicalText()).orElse("")
            + cultivation.map(value -> "|cultivation=" + value.canonicalText()).orElse(""); }
}
