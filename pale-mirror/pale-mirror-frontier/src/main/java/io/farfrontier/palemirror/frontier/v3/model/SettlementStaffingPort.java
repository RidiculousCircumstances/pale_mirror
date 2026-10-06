package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Set;
import java.util.Objects;

/** Family-owned staffing demand. The common allocator cannot inspect family phases or recipes. */
public interface SettlementStaffingPort {
    ResidentWorkKind kind();
    HumanCapability capability();
    Demand assess(FrontierWorldState state, SubjectId settlement, SettlementLabourRules.Entry rules);

    record Demand(ResidentWorkKind kind, HumanCapability capability, int targetWorkers,
                  int minimumLocalStaff, int priority, Set<SubjectId> retainedWorkers) {
        public Demand {
            Objects.requireNonNull(kind); Objects.requireNonNull(capability);
            retainedWorkers = Set.copyOf(retainedWorkers);
            if (targetWorkers < 0 || targetWorkers > ResidentWorkPermissions.MAX_WORKERS_PER_KIND
                    || minimumLocalStaff < 0 || minimumLocalStaff > targetWorkers || priority < 1 || priority > 255
                    || retainedWorkers.size() > ResidentWorkPermissions.MAX_WORKERS_PER_KIND)
                throw new IllegalArgumentException("invalid family staffing demand");
        }
    }
}
