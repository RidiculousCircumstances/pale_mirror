package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.model.ActorItemTransferStep;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation;
import java.util.Objects;

/** Exact durable intent, never proof that a block/resource/equipment effect actually happened. */
public sealed interface ExtractionPhysicalStep permits ExtractionPhysicalStep.Equipment, ExtractionPhysicalStep.BlockWork,
        ExtractionPhysicalStep.Cargo {
    ActorHotObservation observation();
    record Equipment(ActorHotObservation observation, io.farfrontier.palemirror.frontier.v3.model.InventoryCustody.ContainerSlot slot,
            long containerEpoch) implements ExtractionPhysicalStep {
        public Equipment { Objects.requireNonNull(observation); Objects.requireNonNull(slot);
            if (containerEpoch < 1) throw new IllegalArgumentException("equipment effect lacks current container custody"); }
    }
    record BlockWork(ActorHotObservation observation, BlockExtraction extraction, int carriedBefore, long sourceEpoch) implements ExtractionPhysicalStep {
        public BlockWork {
            Objects.requireNonNull(observation); Objects.requireNonNull(extraction);
            if (!observation.actuation().execution().equals(extraction.execution()) || carriedBefore < 0 || carriedBefore >= 64
                    || extraction.output().size() != 1 || carriedBefore + extraction.output().getFirst().quantity() > 64 || sourceEpoch < 1)
                throw new IllegalArgumentException("block work lacks its exact actor and bounded output-stack preimage");
        }
    }
    record Cargo(ActorItemTransferStep transfer) implements ExtractionPhysicalStep {
        public Cargo { Objects.requireNonNull(transfer); }
        @Override public ActorHotObservation observation() { return transfer.observation(); }
    }
}
