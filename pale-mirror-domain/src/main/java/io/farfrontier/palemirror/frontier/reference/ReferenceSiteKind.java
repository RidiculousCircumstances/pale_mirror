package io.farfrontier.palemirror.frontier.reference;

/** Exact Java names and output mapping for Python {@code SiteKind}. */
public enum ReferenceSiteKind {
    FARM(ReferenceResource.FOOD),
    MINE(ReferenceResource.ORE),
    FOREST(ReferenceResource.TIMBER),
    POWER(ReferenceResource.ENERGY);

    private final ReferenceResource resource;

    ReferenceSiteKind(ReferenceResource resource) {
        this.resource = resource;
    }

    public ReferenceResource resource() {
        return resource;
    }
}
