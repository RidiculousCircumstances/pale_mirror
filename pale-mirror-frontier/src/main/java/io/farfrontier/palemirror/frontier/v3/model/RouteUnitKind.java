package io.farfrontier.palemirror.frontier.v3.model;

/** The bounded route owner which retains one exact human organization. */
public enum RouteUnitKind {
    PATROL,
    CARGO_ESCORT;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
