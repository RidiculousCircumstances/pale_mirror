package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Bounded learned capability of one exact resident.  Capabilities are separate
 * from a resident's bootstrap affinity, profession and current assignment.
 */
public enum HumanCapability {
    AGRICULTURE,
    EXTRACTION,
    INDUSTRY,
    ENGINEERING,
    LOGISTICS,
    MEDICINE,
    SECURITY,
    CIVIC;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
