package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.Objects;
public record ExtractionHandCustodyObserved(SubjectId jobId, ActorActuationId identity, Boundary boundary,
        FungiblePhysicalObservation.Stack stack) implements FrontierPayload {
    public enum Boundary {
        MATERIALIZED(1), SAVED_DEPARTURE(2);
        private final int tag; Boundary(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Boundary decode(int tag) { return switch (tag) { case 1 -> MATERIALIZED; case 2 -> SAVED_DEPARTURE;
            default -> throw new IllegalArgumentException("unknown extraction hand boundary"); }; }
    }
    public ExtractionHandCustodyObserved { Objects.requireNonNull(jobId); Objects.requireNonNull(identity); Objects.requireNonNull(boundary); Objects.requireNonNull(stack); }
    @Override public String type() { return "frontier.extraction_hand_custody_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
