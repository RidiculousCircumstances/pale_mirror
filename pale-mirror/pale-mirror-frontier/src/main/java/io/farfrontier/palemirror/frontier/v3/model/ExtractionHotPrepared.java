package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPhysicalStep;
import java.util.Objects;
public record ExtractionHotPrepared(SubjectId jobId, ExtractionPhysicalStep step) implements FrontierPayload {
    public ExtractionHotPrepared { Objects.requireNonNull(jobId); Objects.requireNonNull(step); }
    @Override public String type() { return "frontier.extraction_hot_prepared"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
