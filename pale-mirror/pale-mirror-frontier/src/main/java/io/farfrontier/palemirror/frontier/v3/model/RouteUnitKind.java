package io.farfrontier.palemirror.frontier.v3.model;

/** The bounded route owner which retains one exact human organization. */
public enum RouteUnitKind {
    PATROL;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
