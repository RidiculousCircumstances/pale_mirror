package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable single-product recipe. Inventory custody and labour are separate owners. */
public record ProductionRecipe(Id id, ProductionStationSpec.Capability capability,
                               Ingredient ingredient, Product product, long heatPerBatch,
                               ProcessingMode processingMode, ProductionRecipeOutcome outcome) {
    /** Existing resource recipes explicitly use fungible output; equipment recipes declare their identity semantics. */
    public ProductionRecipe(Id id, ProductionStationSpec.Capability capability, Ingredient ingredient,
            Product product, long heatPerBatch, ProcessingMode processingMode) {
        this(id, capability, ingredient, product, heatPerBatch, processingMode, new ProductionRecipeOutcome.Fungible());
    }
    public record Id(String value) {
        public Id { requireItemKey(value); }
    }
    public enum ProcessingMode {
        ATTENDED(1), AUTOMATIC(2);
        private final int wireTag;
        ProcessingMode(int wireTag) { this.wireTag = wireTag; }
        public int wireTag() { return wireTag; }
        public static ProcessingMode fromWireTag(int tag) {
            for (var mode : values()) if (mode.wireTag == tag) return mode;
            throw new IllegalArgumentException("unknown processing mode " + tag);
        }
    }
    /** Explicit frozen membership, including tag expansion when supplied by an adapter. */
    public record Ingredient(Set<String> acceptedItems, int quantity) {
        public Ingredient {
            acceptedItems = Set.copyOf(Objects.requireNonNull(acceptedItems));
            if (acceptedItems.isEmpty() || acceptedItems.size() > 256 || quantity < 1 || quantity > 64)
                throw new IllegalArgumentException("invalid bounded recipe ingredient");
            acceptedItems.forEach(ProductionRecipe::requireItemKey);
        }
        public boolean accepts(String itemKind) { return acceptedItems.contains(itemKind); }
    }
    public record Product(String itemKind, int quantity) {
        public Product {
            requireItemKey(itemKind);
            if (quantity < 1 || quantity > 64) throw new IllegalArgumentException("invalid recipe product quantity");
        }
    }
    public ProductionRecipe {
        Objects.requireNonNull(id); Objects.requireNonNull(capability);
        Objects.requireNonNull(ingredient); Objects.requireNonNull(product); Objects.requireNonNull(processingMode);
        Objects.requireNonNull(outcome);
        if (!(outcome instanceof ProductionRecipeOutcome.Fungible) && product.quantity() != 1)
            throw new IllegalArgumentException("equipment recipe declares one individual output per operation");
        if (heatPerBatch < 0 || heatPerBatch > 1_000_000_000L)
            throw new IllegalArgumentException("invalid recipe heat requirement");
    }
    public int outputForInput(int quantity) {
        if (quantity < 1 || quantity > 64 || quantity % ingredient.quantity() != 0)
            throw new IllegalArgumentException("input does not form complete bounded recipe batches");
        int output = Math.multiplyExact(quantity / ingredient.quantity(), product.quantity());
        if (!(outcome instanceof ProductionRecipeOutcome.Fungible) && output != 1)
            throw new IllegalArgumentException("individual equipment operations cannot batch identities");
        if (output > 64) throw new IllegalArgumentException("recipe output exceeds its bounded transport portion");
        return output;
    }
    public void requireTransformation(ProductionStationSpec.Capability stationCapability,
                                      Map<String, Integer> inputs, String outputKind, int outputQuantity) {
        Objects.requireNonNull(inputs);
        if (stationCapability != capability || inputs.isEmpty()
                || inputs.entrySet().stream().anyMatch(entry -> !ingredient.accepts(entry.getKey()) || entry.getValue() < 1)
                || !product.itemKind().equals(outputKind)
                || outputForInput(inputs.values().stream().reduce(0, Math::addExact)) != outputQuantity)
            throw new IllegalArgumentException("inputs/output do not satisfy declared recipe " + id.value());
    }
    public String canonicalText() {
        return id.value() + ":" + capability.wireTag() + ":"
                + ingredient.acceptedItems().stream().sorted().collect(java.util.stream.Collectors.joining(","))
                + ":" + ingredient.quantity() + ":" + product.itemKind() + ":" + product.quantity()
                + ":" + heatPerBatch + ":" + processingMode.wireTag()
                + (outcome instanceof ProductionRecipeOutcome.Fungible ? "" : ":" + outcome.canonicalText());
    }
    static void requireItemKey(String key) {
        if (key == null || !key.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}"))
            throw new IllegalArgumentException("recipe requires a namespaced identity");
    }
}
