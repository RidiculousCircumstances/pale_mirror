package io.farfrontier.palemirror.frontier.v3.model;

/** One visible, resource-backed modification installed on an exact bioform. */
public enum BioformMutation {
    EXPLOSIVE,
    MUCUS,
    ARMORED,
    SPORE,
    CARRIER;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
