package io.farfrontier.palemirror.frontier;

/** Productive identity of a settlement; its residents and initial reserves follow this focus. */
public enum FrontierSettlementFocus {
    AGRARIAN, INDUSTRIAL, FORESTRY;

    static FrontierSettlementFocus forIndex(int index) {
        return values()[Math.floorMod(index, values().length)];
    }
}
