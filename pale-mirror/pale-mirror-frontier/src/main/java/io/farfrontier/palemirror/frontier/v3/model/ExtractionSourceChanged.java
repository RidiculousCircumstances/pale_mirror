package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.util.*;

/** An external physical fact is not a mining output receipt. */
public record ExtractionSourceChanged(ExtractionTarget target, BlockExtraction.Block actual,
        long sourceEpoch, long sourceReplicaRevision, Optional<UnappliedEffect> unapplied) implements FrontierPayload {
    public record UnappliedEffect(SubjectId jobId, ExtractionPhysicalStep.BlockWork step,
            List<FungiblePhysicalObservation.Stack> unchangedHand) {
        public UnappliedEffect { Objects.requireNonNull(jobId); Objects.requireNonNull(step); unchangedHand = List.copyOf(unchangedHand); }
    }
    public ExtractionSourceChanged {
        Objects.requireNonNull(target); Objects.requireNonNull(actual); Objects.requireNonNull(unapplied);
        if (sourceEpoch < 1 || sourceReplicaRevision < 1) throw new IllegalArgumentException("source change lacks its captured custody fence");
    }
    @Override public String type() { return "frontier.extraction_source_changed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
