package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Immutable single-product recipe. Inventory custody and labour are separate owners. */
public record ProductionRecipe(Id id, ProductionStationSpec.Capability capability,
                               List<Ingredient> ingredients, Product product, boolean requiresCombustion,
                               ProcessingMode processingMode, ProductionRecipeOutcome outcome, Optional<Id> nativeCraftingRecipe) {
    public ProductionRecipe(Id id, ProductionStationSpec.Capability capability, List<Ingredient> ingredients, Product product,
            boolean requiresCombustion, ProcessingMode processingMode, ProductionRecipeOutcome outcome) {
        this(id, capability, ingredients, product, requiresCombustion, processingMode, outcome, Optional.empty());
    }
    public ProductionRecipe withNativeCraftingRecipe(Id nativeId) {
        return new ProductionRecipe(id, capability, ingredients, product, requiresCombustion, processingMode, outcome, Optional.of(nativeId));
    }
    /** Existing resource recipes explicitly use fungible output; equipment recipes declare their identity semantics. */
    public ProductionRecipe(Id id, ProductionStationSpec.Capability capability, Ingredient ingredient,
            Product product, boolean requiresCombustion, ProcessingMode processingMode) {
        this(id, capability, List.of(ingredient), product, requiresCombustion, processingMode, new ProductionRecipeOutcome.Fungible());
    }
    public ProductionRecipe(Id id, ProductionStationSpec.Capability capability, Ingredient ingredient,
            Product product, boolean requiresCombustion, ProcessingMode processingMode, ProductionRecipeOutcome outcome) {
        this(id, capability, List.of(ingredient), product, requiresCombustion, processingMode, outcome);
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
    public record Ingredient(InputId id, Set<String> acceptedItems, int quantity) {
        /** A single material requirement has this explicit role; two such requirements are rejected as duplicate declarations. */
        public Ingredient(Set<String> acceptedItems, int quantity) { this(new InputId("material"), acceptedItems, quantity); }
        public Ingredient {
            Objects.requireNonNull(id, "declared recipe input");
            acceptedItems = Set.copyOf(Objects.requireNonNull(acceptedItems));
            if (acceptedItems.isEmpty() || acceptedItems.size() > 256 || quantity < 1 || quantity > 64)
                throw new IllegalArgumentException("invalid bounded recipe ingredient");
            acceptedItems.forEach(ProductionRecipe::requireItemKey);
        }
        public boolean accepts(String itemKind) { return acceptedItems.contains(itemKind); }
    }
    public record InputId(String value) implements Comparable<InputId> {
        public InputId {
            if (value == null || !value.matches("[a-z][a-z0-9_]{0,31}"))
                throw new IllegalArgumentException("invalid recipe input identity");
        }
        @Override public int compareTo(InputId other) { return value.compareTo(other.value); }
    }
    public record Product(String itemKind, int quantity) {
        public Product {
            requireItemKey(itemKind);
            if (quantity < 1 || quantity > 64) throw new IllegalArgumentException("invalid recipe product quantity");
        }
    }
    public ProductionRecipe {
        Objects.requireNonNull(id); Objects.requireNonNull(capability);
        ingredients = List.copyOf(Objects.requireNonNull(ingredients));
        if (ingredients.isEmpty() || ingredients.size() > 9
                || ingredients.stream().map(Ingredient::id).distinct().count() != ingredients.size()
                || ingredients.stream().mapToInt(Ingredient::quantity).sum() > 64)
            throw new IllegalArgumentException("recipe needs distinct bounded input requirements");
        Objects.requireNonNull(product); Objects.requireNonNull(processingMode);
        Objects.requireNonNull(outcome); nativeCraftingRecipe = Objects.requireNonNull(nativeCraftingRecipe);
        if (!(outcome instanceof ProductionRecipeOutcome.Fungible) && product.quantity() != 1)
            throw new IllegalArgumentException("equipment recipe declares one individual output per operation");
    }
    public int outputForInput(int quantity) {
        if (quantity < 1 || quantity > 64 || quantity % inputUnitsPerBatch() != 0)
            throw new IllegalArgumentException("input does not form complete bounded recipe batches");
        int output = Math.multiplyExact(quantity / inputUnitsPerBatch(), product.quantity());
        if (!(outcome instanceof ProductionRecipeOutcome.Fungible) && output != 1)
            throw new IllegalArgumentException("individual equipment operations cannot batch identities");
        if (output > 64) throw new IllegalArgumentException("recipe output exceeds its bounded transport portion");
        return output;
    }
    /** Only specialized single-material callers may request this narrower contract. */
    public Ingredient singleIngredient() {
        if (ingredients.size() != 1) throw new IllegalArgumentException("recipe requires a complete multiple-input allocation: " + id.value());
        return ingredients.getFirst();
    }
    public int inputUnitsPerBatch() { return ingredients.stream().mapToInt(Ingredient::quantity).sum(); }
    public boolean acceptsInput(String itemKind) { return ingredients.stream().anyMatch(input -> input.accepts(itemKind)); }
    public Optional<ProductionMaterialAllocation> allocate(Map<String, Integer> available, int batches) {
        return ProductionMaterialAllocation.select(this, available, batches);
    }
    public void requireTransformation(ProductionStationSpec.Capability stationCapability,
                                      Map<String, Integer> inputs, String outputKind, int outputQuantity) {
        Objects.requireNonNull(inputs);
        if (stationCapability != capability || inputs.isEmpty()
                || inputs.entrySet().stream().anyMatch(entry -> !acceptsInput(entry.getKey()) || entry.getValue() < 1)
                || !product.itemKind().equals(outputKind)
                || outputForInput(inputs.values().stream().reduce(0, Math::addExact)) != outputQuantity)
            throw new IllegalArgumentException("inputs/output do not satisfy declared recipe " + id.value());
        int batches = outputQuantity / product.quantity();
        if (allocate(inputs, batches).filter(allocation -> allocation.quantities().equals(inputs)).isEmpty())
            throw new IllegalArgumentException("recipe lacks its exact simultaneous material requirements: " + id.value());
    }
    /** Forecast at baseline work speed. Actual burning uses the machine interval and worker attendance. */
    public long nominalBurnTicksPerBatch(WorkCatalog work) {
        return requiresCombustion ? work.require(product.itemKind(), WorkOperation.PRODUCE).workUnits() : 0L;
    }
    public String canonicalText() {
        return id.value() + "|native:" + nativeCraftingRecipe.map(Id::value).orElse("none") + ":" + capability.wireTag() + ":"
                + ingredients.stream().sorted(Comparator.comparing(Ingredient::id))
                    .map(input -> input.id().value() + "=" + input.acceptedItems().stream().sorted()
                        .collect(java.util.stream.Collectors.joining(",")) + "*" + input.quantity())
                    .collect(java.util.stream.Collectors.joining(";"))
                + ":" + product.itemKind() + ":" + product.quantity()
                + ":fire=" + requiresCombustion + ":" + processingMode.wireTag()
                + (outcome instanceof ProductionRecipeOutcome.Fungible ? "" : ":" + outcome.canonicalText());
    }
    static void requireItemKey(String key) {
        if (key == null || !key.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}"))
            throw new IllegalArgumentException("recipe requires a namespaced identity");
    }
}
