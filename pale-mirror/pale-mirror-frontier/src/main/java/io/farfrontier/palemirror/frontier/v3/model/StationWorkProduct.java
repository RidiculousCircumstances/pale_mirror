package io.farfrontier.palemirror.frontier.v3.model;

/** Product representation is recipe-declared, independent of the consumed materials' custody. */
public final class StationWorkProduct {
    public enum Representation {
        EXACT_ITEM(1), RESOURCE_LOT(2);
        private final int tag;
        Representation(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Representation fromWireTag(int tag) {
            for (var value : values()) if (value.tag == tag) return value;
            throw new IllegalArgumentException("unknown station product representation");
        }
        public FrontierDomainRelationships.EntityKind entityKind() {
            return switch (this) {
                case EXACT_ITEM -> FrontierDomainRelationships.EntityKind.EXACT_ITEM;
                case RESOURCE_LOT -> FrontierDomainRelationships.EntityKind.RESOURCE_LOT;
            };
        }
    }
    public static boolean individual(FrontierWorldState state, ProductionJob job) {
        return individual(state.bootstrap().ruleset().production(), job);
    }
    public static boolean individual(ProductionCatalog catalog, ProductionJob job) {
        return !(catalog.require(job.stationWork().orElseThrow().recipeId()).outcome() instanceof ProductionRecipeOutcome.Fungible);
    }
    /** Older exact ingredient jobs still return an exact resource stack; this is not an individual-tool recipe. */
    public static boolean exact(FrontierWorldState state, ProductionJob job) {
        return job.stationWork().orElseThrow().outputRepresentation() == Representation.EXACT_ITEM;
    }
    private StationWorkProduct() { }
}
