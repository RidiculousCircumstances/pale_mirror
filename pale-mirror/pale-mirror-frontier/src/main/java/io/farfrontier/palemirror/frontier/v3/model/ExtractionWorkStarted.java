package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionWork;
import java.util.Objects;
public record ExtractionWorkStarted(ExtractionWork work, long expectedOrdinal) implements FrontierPayload {
    public ExtractionWorkStarted { Objects.requireNonNull(work); if (expectedOrdinal < 1) throw new IllegalArgumentException("invalid mining admission ordinal"); }
    @Override public String type() { return "frontier.extraction_work_started"; }
}
