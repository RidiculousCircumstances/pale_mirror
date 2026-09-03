package io.farfrontier.palemirror.frontier.v3.model;

/** Semantic role of one compiled graybox cell, independent of Minecraft block choice. */
public enum GrayboxSemanticPart {
    FOUNDATION,
    WALL,
    ROOF,
    HIVE_TISSUE,
    ROUTE_SURFACE,
    /** Load-bearing graybox fill below a surveyed raised route surface. */
    ROUTE_FOUNDATION,
    PUBLIC_ACCESS_SURFACE,
    INFECTION_SURFACE,
    /**
     * A project-owned, temporary work floor.  It is neither an accepted route cell nor a
     * generic structure: losing it stops the exact construction project instead of allowing a
     * materializer to recreate it or treating it as completed infrastructure.
     */
    WORKSITE_STAGING
    ,
    /** One exact occupied cocoon, owned by its bioform rather than the enclosing organ. */
    COCOON,
    /** A workshop's exact material hand-off surface; it remains traversable floor, not a container. */
    WORKSHOP_INPUT_STATION,
    /** A workshop's exact active processing surface; it remains traversable floor, not a hidden machine. */
    WORKSHOP_PROCESS_STATION
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
