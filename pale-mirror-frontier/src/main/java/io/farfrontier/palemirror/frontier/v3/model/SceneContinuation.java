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
        SceneContinuation.FinalizeProductionWork {
    Kind kind();

    enum Kind {
        NONE,
        RESUME_OPERATION,
        FAIL_OPERATION,
        RESUME_ENGAGEMENT,
        RESUME_SETTLEMENT_ASSAULT,
        FINALIZE_PRODUCTION_WORK
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
}
