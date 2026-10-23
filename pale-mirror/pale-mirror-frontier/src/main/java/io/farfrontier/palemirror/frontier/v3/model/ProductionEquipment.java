package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;
import java.util.Set;

/** Equipment type is not its installed instance, facility or current worker. */
public record ProductionEquipment(Id id, String portableItemKind,
                                  Set<ProductionStationSpec.Capability> capabilities,
                                  Set<ProductionRecipe.ProcessingMode> processingModes) {
    public record Id(String value) {
        public Id { ProductionRecipe.requireItemKey(value); }
    }
    public static final Id BENCH = new Id("pale_mirror:production_bench");
    public static final Id SMELTER = new Id("pale_mirror:smelter");
    public static final Id FORGE = new Id("pale_mirror:forge");
    public ProductionEquipment {
        Objects.requireNonNull(id); ProductionRecipe.requireItemKey(portableItemKind);
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities));
        processingModes = Set.copyOf(Objects.requireNonNull(processingModes));
        if (capabilities.isEmpty() || processingModes.isEmpty()) throw new IllegalArgumentException("equipment has no declared processing capability");
    }
    public void require(ProductionRecipe recipe) {
        if (!capabilities.contains(recipe.capability()) || !processingModes.contains(recipe.processingMode()))
            throw new IllegalArgumentException("equipment does not support the declared recipe");
    }
    public String canonicalText() {
        return id.value() + ":" + portableItemKind + ":"
                + capabilities.stream().map(ProductionStationSpec.Capability::wireTag).sorted().toList() + ":"
                + processingModes.stream().map(ProductionRecipe.ProcessingMode::wireTag).sorted().toList();
    }
    public static ProductionEquipment bench() {
        return new ProductionEquipment(BENCH, "pale_mirror:production_bench",
                Set.of(ProductionStationSpec.Capability.BAKING), Set.of(ProductionRecipe.ProcessingMode.ATTENDED,
                        ProductionRecipe.ProcessingMode.AUTOMATIC));
    }
    public static ProductionEquipment smelter() {
        return new ProductionEquipment(SMELTER, "pale_mirror:smelter",
                Set.of(ProductionStationSpec.Capability.SMELTING), Set.of(ProductionRecipe.ProcessingMode.AUTOMATIC));
    }
    public static ProductionEquipment forge() {
        return new ProductionEquipment(FORGE, "pale_mirror:forge", Set.of(ProductionStationSpec.Capability.FABRICATION),
                Set.of(ProductionRecipe.ProcessingMode.ATTENDED));
    }
}
