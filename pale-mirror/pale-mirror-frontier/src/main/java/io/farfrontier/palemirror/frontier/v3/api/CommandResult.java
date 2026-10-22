package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;

/** Result of a single command boundary; rejection is guaranteed not to mutate canonical state. */
public sealed interface CommandResult permits CommandResult.Accepted, CommandResult.Rejected, CommandResult.Pending {
    /** No published mutation or permission for an external effect. A predecessor wait
     * has NOT admitted this command; a durability wait has retained its exact transaction. */
    record Pending(CommandId commandId, Revision revision, Reason reason) implements CommandResult {
        public enum Reason { PREDECESSOR, DURABILITY }
        public Pending {
            Objects.requireNonNull(commandId); Objects.requireNonNull(revision); Objects.requireNonNull(reason);
        }
    }
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
