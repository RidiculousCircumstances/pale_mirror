package io.farfrontier.palemirror.frontier.v3.model;

/** Lifecycle of one exact resident's bounded work agreement. */
public enum EmploymentContractStatus {
    ACTIVE,
    SUSPENDED,
    TERMINATED
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
