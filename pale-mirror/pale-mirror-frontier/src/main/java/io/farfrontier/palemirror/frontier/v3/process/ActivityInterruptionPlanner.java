package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

import java.util.List;
import java.util.Objects;

/** Activity selection depends on this port, never on a movement or profession's private phases. */
public interface ActivityInterruptionPlanner {
    Assessment assess(FrontierWorldState state, SubjectId residentId, long atTick);

    sealed interface Assessment permits Ready, Waiting { }
    /** Owner-local transitions are planned against the same immutable input revision. */
    record Ready(SubjectId residentId, FrontierWorldState basis, FrontierWorldState following, List<ProposedEvent> events) implements Assessment {
        public Ready {
            Objects.requireNonNull(residentId, "interruption resident");
            Objects.requireNonNull(basis, "interruption basis");
            Objects.requireNonNull(following, "interruption result");
            events = List.copyOf(events);
        }
        public Ready validate(FrontierWorldState current, SubjectId residentId) {
            // A registered owner may wake its exact retained job as well as this resident.
            // Correlate the port result explicitly, not by assuming all emitted subjects are bodies.
            if (basis != current || !this.residentId.equals(residentId))
                throw new IllegalArgumentException("interruption result has a stale basis or foreign subject");
            return this;
        }
    }
    record Waiting(Reason reason) implements Assessment {
        public Waiting { Objects.requireNonNull(reason, "interruption reason"); }
    }
    enum Reason { SERVICE_CLEARANCE, AUTHORITY_HANDOFF }
}
