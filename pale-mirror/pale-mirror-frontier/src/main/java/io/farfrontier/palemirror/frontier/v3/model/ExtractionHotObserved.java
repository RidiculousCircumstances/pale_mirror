package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionPhysicalStep;
import java.util.*;
public record ExtractionHotObserved(SubjectId jobId, ExtractionPhysicalStep step,
        List<FungiblePhysicalObservation.Stack> remainingSource, List<FungiblePhysicalObservation.Stack> destination,
        Optional<io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Block> externalSuccessor) implements FrontierPayload {
    public ExtractionHotObserved(SubjectId jobId, ExtractionPhysicalStep step, List<FungiblePhysicalObservation.Stack> remainingSource,
            List<FungiblePhysicalObservation.Stack> destination) {
        this(jobId, step, remainingSource, destination, Optional.empty());
    }
    public ExtractionHotObserved {
        Objects.requireNonNull(jobId); Objects.requireNonNull(step);
        remainingSource = List.copyOf(remainingSource); destination = List.copyOf(destination);
        Objects.requireNonNull(externalSuccessor);
        if (externalSuccessor.isPresent() && !(step instanceof ExtractionPhysicalStep.BlockWork))
            throw new IllegalArgumentException("only a block effect may retain a subsequent external block observation");
        if (remainingSource.size() > 27 || destination.size() > 27) throw new IllegalArgumentException("unbounded mining resource witness");
    }
    @Override public String type() { return "frontier.extraction_hot_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
