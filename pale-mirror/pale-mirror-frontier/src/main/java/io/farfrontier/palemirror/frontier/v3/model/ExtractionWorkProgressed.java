package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionWorkDecision;
/** Exact owner operation, not an arbitrary replacement aggregate. */
public record ExtractionWorkProgressed(SubjectId jobId, long expectedRevision, Operation operation,
        Optional<ExtractionWorkDecision> decision) implements FrontierPayload {
    public enum Operation {
        TAKE_TOOL(1), LABOUR(2), EXTRACT_BLOCK(3), STORE(4), RETURN_TOOL(5), SELECT_REACHABLE(6), SELECT_SOURCE(7), END_WORK(8), SELECT_STORAGE(9),
        RELOCATE_SITE(10), STORE_PART(11);
        private final int tag; Operation(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Operation decode(int tag) {
            for (var operation : values()) if (operation.tag == tag) return operation;
            throw new IllegalArgumentException("unknown extraction operation");
        }
    }
    public ExtractionWorkProgressed {
        Objects.requireNonNull(jobId); Objects.requireNonNull(operation);
        Objects.requireNonNull(decision);
        if (expectedRevision < 1) throw new IllegalArgumentException("invalid extraction predecessor");
        boolean selecting = operation == Operation.SELECT_SOURCE || operation == Operation.SELECT_REACHABLE
                || operation == Operation.SELECT_STORAGE || operation == Operation.RELOCATE_SITE;
        if (selecting != decision.isPresent() || decision.filter(value -> !value.execution().activityOwnerId().equals(jobId)
                || switch (operation) {
                    case SELECT_SOURCE -> !(value instanceof ExtractionWorkDecision.Source || value instanceof ExtractionWorkDecision.Finished
                            || value instanceof ExtractionWorkDecision.StorePart);
                    case SELECT_REACHABLE -> !(value instanceof ExtractionWorkDecision.Source);
                    case SELECT_STORAGE -> !(value instanceof ExtractionWorkDecision.Storage);
                    case RELOCATE_SITE -> !(value instanceof ExtractionWorkDecision.Relocate);
                    default -> true;
                }).isPresent()) throw new IllegalArgumentException("extraction operation lacks its exhaustive selected successor");
    }
    public ExtractionWorkProgressed(SubjectId jobId, long expectedRevision, Operation operation) {
        this(jobId, expectedRevision, operation, Optional.empty());
    }
    @Override public String type() { return "frontier.extraction_work_progressed"; }
}
