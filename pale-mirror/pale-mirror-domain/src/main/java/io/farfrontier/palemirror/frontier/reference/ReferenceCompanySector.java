package io.farfrontier.palemirror.frontier.reference;

/** Source port of Python's productive {@code CompanySector}. */
public enum ReferenceCompanySector {
    AGRICULTURE(ReferenceResource.FOOD),
    MINING(ReferenceResource.ORE),
    FORESTRY(ReferenceResource.TIMBER),
    ENERGY(ReferenceResource.ENERGY),
    WORKSHOP(ReferenceResource.TOOLS),
    MEDICINE(ReferenceResource.MEDICINE),
    ARMORY(ReferenceResource.WEAPONS);

    private final ReferenceResource output;

    ReferenceCompanySector(ReferenceResource output) {
        this.output = output;
    }

    public ReferenceResource output() {
        return output;
    }

    public static ReferenceCompanySector forSite(ReferenceSiteKind kind) {
        return switch (kind) {
            case FARM -> AGRICULTURE;
            case MINE -> MINING;
            case FOREST -> FORESTRY;
            case POWER -> ENERGY;
        };
    }
}
