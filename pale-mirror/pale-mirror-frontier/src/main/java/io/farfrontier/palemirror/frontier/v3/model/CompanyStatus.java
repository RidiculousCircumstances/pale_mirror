package io.farfrontier.palemirror.frontier.v3.model;

/** Legal lifecycle is separate from an account's current financial balance. */
public enum CompanyStatus { ACTIVE, INSOLVENT, DISSOLVED ;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
