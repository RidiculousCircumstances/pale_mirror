package io.farfrontier.palemirror.frontier.v3.model;

/** Why one otherwise historical work agreement ceased to grant current labour. */
enum EmploymentTerminationReason { DEATH ;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
