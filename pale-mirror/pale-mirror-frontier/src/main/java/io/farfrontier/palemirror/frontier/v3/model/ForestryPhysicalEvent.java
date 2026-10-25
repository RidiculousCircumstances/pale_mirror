package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.ForestryPhysicalStep;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;

/** Exact native intent and outcome declarations; none are inferred from a source postimage. */
public sealed interface ForestryPhysicalEvent extends FrontierPayload {
    record Prepared(SubjectId job, ForestryPhysicalStep step) implements ForestryPhysicalEvent {
        public static final String TYPE = "frontier.forestry_hot_prepared.v1";
        public Prepared { Objects.requireNonNull(job); Objects.requireNonNull(step); }
        @Override public String type() { return TYPE; }
    }
    record Cancelled(SubjectId job, ForestryPhysicalStep step) implements ForestryPhysicalEvent {
        public static final String TYPE = "frontier.forestry_hot_cancelled.v1";
        public Cancelled { Objects.requireNonNull(job); Objects.requireNonNull(step); }
        @Override public String type() { return TYPE; }
    }
    record Observed(SubjectId job, ForestryPhysicalStep step, List<FungiblePhysicalObservation.Stack> remainingSource,
                    List<FungiblePhysicalObservation.Stack> destination, Optional<BlockExtraction.Block> externalSuccessor) implements ForestryPhysicalEvent {
        public static final String TYPE = "frontier.forestry_hot_observed.v1";
        public Observed {
            Objects.requireNonNull(job); Objects.requireNonNull(step); Objects.requireNonNull(externalSuccessor);
            remainingSource = List.copyOf(remainingSource); destination = List.copyOf(destination);
            if (remainingSource.size() > ContainerRecord.MAX_SLOTS || destination.size() > ContainerRecord.MAX_SLOTS || externalSuccessor.isPresent() && !(step instanceof ForestryPhysicalStep.Fell))
                throw new IllegalArgumentException("forest physical outcome has unbounded or foreign evidence");
        }
        @Override public String type() { return TYPE; }
    }
    record HandBoundary(SubjectId job, ActorActuationId identity, Boundary boundary, FungiblePhysicalObservation.Stack stack) implements ForestryPhysicalEvent {
        public static final String TYPE = "frontier.forestry_hand_boundary.v1";
        public enum Boundary {
            MATERIALIZED(1), DEPARTED(2);
            private final int tag; Boundary(int tag) { this.tag = tag; } public int wireTag() { return tag; }
            public static Boundary decode(int tag) { return switch(tag) { case 1 -> MATERIALIZED; case 2 -> DEPARTED;
                default -> throw new IllegalArgumentException("unknown forest hand boundary"); }; }
        }
        public HandBoundary { Objects.requireNonNull(job); Objects.requireNonNull(identity); Objects.requireNonNull(boundary); Objects.requireNonNull(stack); }
        @Override public String type() { return TYPE; }
    }
    record Retired(Observed effect, boolean applied) implements ForestryPhysicalEvent {
        public static final String TYPE = "frontier.forestry_retired_effect.v1";
        public Retired { Objects.requireNonNull(effect); }
        @Override public String type() { return TYPE; }
    }
    record DeathSettled(ActorActuationId identity, long expectedRevision) implements ForestryPhysicalEvent {
        public static final String TYPE = "frontier.forestry_death_settled.v1";
        public DeathSettled { Objects.requireNonNull(identity); if (expectedRevision < 1) throw new IllegalArgumentException("forest death needs exact work revision"); }
        @Override public String type() { return TYPE; }
    }
    @Override default boolean requiresDurableBeforeEffect() { return true; }
}
