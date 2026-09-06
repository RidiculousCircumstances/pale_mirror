package io.farfrontier.palemirror.frontier.v3.model;

/** Stable semantic reason for one retained exact engineering crew journey. */
public enum EngineeringJourneyPurpose {
    MUSTER_DEPOT,
    WORKSITE,
    RETURN_DEPOT;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
