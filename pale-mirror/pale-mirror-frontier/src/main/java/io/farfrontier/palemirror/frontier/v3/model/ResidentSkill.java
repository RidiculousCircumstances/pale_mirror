package io.farfrontier.palemirror.frontier.v3.model;

/** Bounded learned capability of one exact resident. */
public enum ResidentSkill {
    AGRICULTURE, BUILDING, CRAFTING, SECURITY, MEDICINE, LOGISTICS
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
