package io.farfrontier.palemirror.frontier.v3.model;

/** Stable canonical liveness of one exact person or bioform. */
public enum ActorLifeStatus { ALIVE, DEAD ;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
