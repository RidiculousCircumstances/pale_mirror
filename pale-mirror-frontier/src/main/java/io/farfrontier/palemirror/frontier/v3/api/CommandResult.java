package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;

/** Result of a single command boundary; rejection is guaranteed not to mutate canonical state. */
public sealed interface CommandResult permits CommandResult.Accepted, CommandResult.Rejected {
    record Accepted(CommandId commandId, TransactionId transactionId, Revision revision) implements CommandResult {
        public Accepted {
            Objects.requireNonNull(commandId, "command id");
            Objects.requireNonNull(transactionId, "transaction id");
            Objects.requireNonNull(revision, "revision");
        }
    }

    record Rejected(CommandId commandId, Revision revision, CommandRejection rejection) implements CommandResult {
        public Rejected {
            Objects.requireNonNull(commandId, "command id");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(rejection, "rejection");
        }
    }
}
