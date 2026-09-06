package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Stable canonical identities used by trusted adapter/executor boundaries. */
public final class FrontierExecutionSubjects {
    public static final SubjectId PHYSICAL_EXECUTOR = new SubjectId("system:physical_executor");

    private FrontierExecutionSubjects() { }
}
