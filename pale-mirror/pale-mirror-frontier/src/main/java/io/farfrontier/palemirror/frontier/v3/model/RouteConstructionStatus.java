package io.farfrontier.palemirror.frontier.v3.model;

/** Durable lifecycle for a replacement corridor before it becomes active topology. */
public enum RouteConstructionStatus { BUILDING, CONFLICT, READY ;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
