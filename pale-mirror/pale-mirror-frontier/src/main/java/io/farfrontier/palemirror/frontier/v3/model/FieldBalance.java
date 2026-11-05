package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.agriculture.CultivationRules;
import io.farfrontier.palemirror.frontier.v3.model.agriculture.TerritoryAgriculture;
import java.util.*;

/** Authored crop policies; ongoing field work consumes arbitrary explicit cell sets. */
public record FieldBalance(List<CropPolicy> crops, Optional<TerritoryAgriculture> agriculture,
                           Optional<CultivationRules> cultivation) {
    public record CropPolicy(ResourceSiteKind kind, int ordinarySide, int extractiveSide, int minimumStock, boolean marketArea) {
        public CropPolicy(ResourceSiteKind kind, int ordinarySide, int extractiveSide, int minimumStock) {
            this(kind, ordinarySide, extractiveSide, minimumStock, false);
        }
        public CropPolicy {
            Objects.requireNonNull(kind);
            if ((ordinarySide != 8 && ordinarySide != 16) || (extractiveSide != 8 && extractiveSide != 16)
                    || minimumStock < 0 || minimumStock > 4096)
                throw new IllegalArgumentException("invalid crop footprint or minimum stock");
        }
    }
    public FieldBalance {
        crops = List.copyOf(crops);
        Objects.requireNonNull(agriculture); Objects.requireNonNull(cultivation);
        if (crops.isEmpty() || crops.size() > 32 || crops.stream().map(CropPolicy::kind).distinct().count() != crops.size()
                || crops.stream().filter(CropPolicy::marketArea).count() > 1)
            throw new IllegalArgumentException("field crops must have distinct explicit policies");
    }
    /** Explicit wheat-only content composition, not a runtime crop fallback. */
    public FieldBalance(int ordinarySide, int extractiveSide, Optional<TerritoryAgriculture> agriculture,
                        Optional<CultivationRules> cultivation) {
        this(List.of(new CropPolicy(ResourceSiteKind.WHEAT_FIELD, ordinarySide, extractiveSide, 0, true)), agriculture, cultivation);
    }
    public FieldBalance(int ordinarySide, int extractiveSide, Optional<TerritoryAgriculture> agriculture) {
        this(ordinarySide, extractiveSide, agriculture, Optional.empty());
    }
    public FieldBalance(int ordinarySide, int extractiveSide) { this(ordinarySide, extractiveSide, Optional.empty()); }
    public CropPolicy require(ResourceSiteKind kind) {
        return crops.stream().filter(value -> value.kind() == kind).findFirst().orElseThrow(
                () -> new IllegalArgumentException("crop lacks an admitted field policy: " + kind));
    }
    public FieldBalance withCrop(CropPolicy crop) {
        var next = new ArrayList<>(crops.stream().filter(value -> value.kind() != crop.kind()).toList());
        next.add(crop); return new FieldBalance(next, agriculture, cultivation);
    }
    /** The current economic area allocator explicitly manages one declared crop. Other crops keep their admitted area. */
    public ResourceSiteKind cultivationCrop() {
        return crops.stream().filter(CropPolicy::marketArea).map(CropPolicy::kind).findFirst().orElseThrow(
                () -> new IllegalArgumentException("no crop admitted for economic area planning"));
    }
    public int ordinarySide() { return require(ResourceSiteKind.WHEAT_FIELD).ordinarySide(); }
    public int extractiveSide() { return require(ResourceSiteKind.WHEAT_FIELD).extractiveSide(); }
    public static FieldBalance uniform() { return new FieldBalance(16, 16); }
    public String canonicalText() {
        return crops.stream().sorted(Comparator.comparingInt(value -> value.kind().wireTag()))
                .map(value -> value.kind().wireTag() + ":" + value.ordinarySide() + ":" + value.extractiveSide()
                        + ":" + value.minimumStock() + ":marketArea=" + value.marketArea() + ":" + value.kind().crop().canonicalText())
                .collect(java.util.stream.Collectors.joining(";"))
                + agriculture.map(value -> "|agriculture=" + value.canonicalText()).orElse("")
                + cultivation.map(value -> "|cultivation=" + value.canonicalText()).orElse("")
                + "|soilPreparation=" + ResourceFieldCycle.Soil.catalog().canonicalText();
    }
}
