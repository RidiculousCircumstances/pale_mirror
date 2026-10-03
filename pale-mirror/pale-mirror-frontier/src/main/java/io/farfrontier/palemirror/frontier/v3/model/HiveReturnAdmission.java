package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionGroup;
import java.util.Objects;

/** Complete nominal aftermath declaration, selected at admission and retained in the resolution event. */
public sealed interface HiveReturnAdmission {
    record Independent() implements HiveReturnAdmission { }
    record Completed(SubjectId mobilizationId) implements HiveReturnAdmission {
        public Completed { Objects.requireNonNull(mobilizationId, "completed hive parent"); }
    }
    record Returning(SubjectId mobilizationId, HiveReturnAssembly assembly,
                     ActorExecutionGroup executions) implements HiveReturnAdmission {
        public Returning {
            Objects.requireNonNull(mobilizationId, "returning hive parent");
            Objects.requireNonNull(assembly, "retained return route");
            Objects.requireNonNull(executions).requireDeclaration(ActorActivityKind.HIVE_TASK_RETURN,
                    mobilizationId, assembly.members().keySet());
            if (assembly.complete()) throw new IllegalArgumentException("completed return cannot admit travel");
        }
    }
}
