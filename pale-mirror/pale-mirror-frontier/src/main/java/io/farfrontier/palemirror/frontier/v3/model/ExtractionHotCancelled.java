package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPhysicalStep;
import java.util.Objects;

/** Native journal proves that this exact preparation never mutated the world. */
public record ExtractionHotCancelled(SubjectId jobId, ExtractionPhysicalStep step) implements FrontierPayload {
    public ExtractionHotCancelled { Objects.requireNonNull(jobId); Objects.requireNonNull(step); }
    @Override public String type() { return "frontier.extraction_hot_cancelled"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
