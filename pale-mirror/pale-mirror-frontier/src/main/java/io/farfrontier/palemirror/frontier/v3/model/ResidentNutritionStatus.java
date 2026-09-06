package io.farfrontier.palemirror.frontier.v3.model;

/** Current food security of one exact resident; disease and physical vitality remain separate owners. */
public enum ResidentNutritionStatus {
    NOURISHED,
    HUNGRY,
    STARVING
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
