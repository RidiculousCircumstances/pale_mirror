package io.farfrontier.palemirror.frontier.v3.model;

/** Bounded local purpose chosen by the domain while its exact actor is HOT. */
public enum AmbientGoalKind {
    WORK, GUARD, PATROL, SCOUT_PATROL, TRANSIT, OPERATION_ASSEMBLY, ENGINEERING_ASSEMBLY, HIVE_TASK_ASSEMBLY
;

    public int wireTag() { return FrontierWireTags.tag(this); }
}
