package io.farfrontier.palemirror.frontier.v3.model.forestry;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation;
import io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction;
import io.farfrontier.palemirror.frontier.v3.model.tooling.ToolUse;
import java.util.Objects;

/** Retained exact effect preimages. Physics, storage and resource owners confirm them once. */
public sealed interface ForestryPhysicalStep {
    ActorHotObservation observation();
    record Equipment(WorkEquipmentStep transfer) implements ForestryPhysicalStep {
        public Equipment { Objects.requireNonNull(transfer); }
        @Override public ActorHotObservation observation() { return transfer.observation(); }
    }
    record Cargo(ActorItemTransferStep transfer) implements ForestryPhysicalStep {
        public Cargo { Objects.requireNonNull(transfer); }
        @Override public ActorHotObservation observation() { return transfer.observation(); }
    }
    record Fell(ActorHotObservation observation, BlockExtraction effect, int child, int carriedBefore,
                long sourceEpoch, ToolUse toolUse) implements ForestryPhysicalStep {
        public Fell {
            Objects.requireNonNull(observation); Objects.requireNonNull(effect); Objects.requireNonNull(toolUse);
            if (child < 0 || child >= io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtractionBatch.MAX_BLOCKS
                    || carriedBefore < 0 || carriedBefore > 64 || sourceEpoch < 1
                    || !observation.actuation().execution().equals(effect.execution()))
                throw new IllegalArgumentException("invalid retained forestry block effect");
        }
    }
}
