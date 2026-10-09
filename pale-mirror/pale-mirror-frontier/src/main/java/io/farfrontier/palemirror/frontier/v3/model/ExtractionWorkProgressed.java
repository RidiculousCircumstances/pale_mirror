package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
/** Exact owner operation, not an arbitrary replacement aggregate. */
public record ExtractionWorkProgressed(SubjectId jobId, long expectedRevision, Operation operation) implements FrontierPayload {
    public enum Operation {
        TAKE_TOOL(1), LABOUR(2), EXTRACT_BLOCK(3), STORE(4), RETURN_TOOL(5), SELECT_REACHABLE(6), SELECT_SOURCE(7), END_WORK(8);
        private final int tag; Operation(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Operation decode(int tag) {
            for (var operation : values()) if (operation.tag == tag) return operation;
            throw new IllegalArgumentException("unknown extraction operation");
        }
    }
    public ExtractionWorkProgressed {
        Objects.requireNonNull(jobId); Objects.requireNonNull(operation);
        if (expectedRevision < 1) throw new IllegalArgumentException("invalid extraction predecessor");
    }
    @Override public String type() { return "frontier.extraction_work_progressed"; }
}
