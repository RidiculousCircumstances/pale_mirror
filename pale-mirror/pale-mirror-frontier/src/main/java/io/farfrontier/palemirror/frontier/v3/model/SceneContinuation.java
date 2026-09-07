package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;

import java.util.Objects;

/**
 * Model-owned decision about what must follow a generic scene lifecycle fact.
 *
 * <p>The scene registry chooses this typed value from the retained cause and
 * canonical state.  The process layer alone turns it into command/event
 * payloads, which keeps model policy free of process-package dependencies.</p>
 */
public sealed interface SceneContinuation permits SceneContinuation.None, SceneContinuation.ResumeOperation,
        SceneContinuation.FailOperation, SceneContinuation.ResumeEngagement, SceneContinuation.ResumeSettlementAssault,
        SceneContinuation.FinalizeProductionWork, SceneContinuation.ResumeProductionCompletion,
        SceneContinuation.ResumeRoutePatrol, SceneContinuation.BlockRoutePatrol,
        SceneContinuation.ResumeResourceSiteHarvest {
    Kind kind();

    enum Kind {
        NONE,
        RESUME_OPERATION,
        FAIL_OPERATION,
        RESUME_ENGAGEMENT,
        RESUME_SETTLEMENT_ASSAULT,
        FINALIZE_PRODUCTION_WORK,
        RESUME_PRODUCTION_COMPLETION,
        RESUME_ROUTE_PATROL,
        BLOCK_ROUTE_PATROL,
        RESUME_RESOURCE_SITE_HARVEST
    }

    record None() implements SceneContinuation {
        @Override public Kind kind() { return Kind.NONE; }
    }

    record ResumeOperation(SubjectId operationId, long dueAt) implements SceneContinuation {
        public ResumeOperation { Objects.requireNonNull(operationId, "operation id"); }
        @Override public Kind kind() { return Kind.RESUME_OPERATION; }
    }

    record FailOperation(SubjectId operationId, String reason) implements SceneContinuation {
        public FailOperation {
            Objects.requireNonNull(operationId, "operation id");
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("operation failure reason");
        }
        @Override public Kind kind() { return Kind.FAIL_OPERATION; }
    }

    record ResumeEngagement(SubjectId engagementId, long dueAt) implements SceneContinuation {
        public ResumeEngagement { Objects.requireNonNull(engagementId, "engagement id"); }
        @Override public Kind kind() { return Kind.RESUME_ENGAGEMENT; }
    }

    record ResumeSettlementAssault(SubjectId assaultId, long dueAt) implements SceneContinuation {
        public ResumeSettlementAssault { Objects.requireNonNull(assaultId, "assault id"); }
        @Override public Kind kind() { return Kind.RESUME_SETTLEMENT_ASSAULT; }
    }

    /** A blocked exact workshop job can be retired only after its retained scene has closed. */
    record FinalizeProductionWork(SceneLeaseId leaseId, SubjectId jobId) implements SceneContinuation {
        public FinalizeProductionWork { Objects.requireNonNull(leaseId, "production scene lease"); Objects.requireNonNull(jobId, "production job id"); }
        @Override public Kind kind() { return Kind.FINALIZE_PRODUCTION_WORK; }
    }

    /**
     * A loaded worker may trigger the physical transformation only after its exact HOT body
     * has released.  The retained job is therefore still present for the release receipt but
     * may safely retire when the later transformation confirmation is committed.
     */
    record ResumeProductionCompletion(SubjectId jobId, long dueAt) implements SceneContinuation {
        public ResumeProductionCompletion { Objects.requireNonNull(jobId, "production job id"); }
        @Override public Kind kind() { return Kind.RESUME_PRODUCTION_COMPLETION; }
    }

    /** A drained exact patrol resumes its same retained COLD cursor; it is not replaced by guard ambience. */
    record ResumeRoutePatrol(SubjectId taskId, long dueAt) implements SceneContinuation {
        public ResumeRoutePatrol { Objects.requireNonNull(taskId, "route-patrol task"); }
        @Override public Kind kind() { return Kind.RESUME_ROUTE_PATROL; }
    }

    /** Unknown post-restart custody blocks the same patrol until explicit inspection, never movement replay. */
    record BlockRoutePatrol(SubjectId taskId) implements SceneContinuation {
        public BlockRoutePatrol { Objects.requireNonNull(taskId, "route-patrol task"); }
        @Override public Kind kind() { return Kind.BLOCK_ROUTE_PATROL; }
    }

    /** A released field worker retains its engine-owned COLD continuation unchanged. */
    record ResumeResourceSiteHarvest(SubjectId jobId) implements SceneContinuation {
        public ResumeResourceSiteHarvest { Objects.requireNonNull(jobId, "resource-site harvest job"); }
        @Override public Kind kind() { return Kind.RESUME_RESOURCE_SITE_HARVEST; }
    }
}
