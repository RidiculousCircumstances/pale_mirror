package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.CommandRejection;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;

import java.util.List;
import java.util.Objects;

/** Pure planning result. Only an accepted plan may cross the reducer transaction boundary. */
public sealed interface CommandPlan permits CommandPlan.Accepted, CommandPlan.Rejected {
    record Accepted(List<ProposedEvent> events) implements CommandPlan {
        public Accepted {
            events = List.copyOf(events);
            if (events.isEmpty()) {
                throw new IllegalArgumentException("accepted command plan must emit at least one event");
            }
        }
    }

    record Rejected(CommandRejection rejection) implements CommandPlan {
        public Rejected {
            Objects.requireNonNull(rejection, "rejection");
        }
    }
}
