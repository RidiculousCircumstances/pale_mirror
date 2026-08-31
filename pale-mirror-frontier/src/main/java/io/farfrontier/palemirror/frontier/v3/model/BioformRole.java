package io.farfrontier.palemirror.frontier.v3.model;

/** Graybox role identity for one exact hive bioform. */
public enum BioformRole {
    WORKER, SCOUT, GUARD, BOMBER
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
