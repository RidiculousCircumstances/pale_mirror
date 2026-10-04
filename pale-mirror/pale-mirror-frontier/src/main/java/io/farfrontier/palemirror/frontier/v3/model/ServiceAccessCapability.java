package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;

/** An owning family interprets its own stages and publishes read-only service demand. */
interface ServiceAccessCapability {
    ServiceAccessDemand.Kind kind();
    ServiceAccessDemand.Priority priority();
    List<ServiceAccessDemand> demands(FrontierWorldState state, SubjectId pointId);
}
