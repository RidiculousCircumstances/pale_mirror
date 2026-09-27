package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Durable bakery phase and the exact custody-account identities used by one bread job. */
public record BakeryWorkState(Phase phase, SubjectId stationId, SubjectId sourceAccountId,
                              SubjectId actorAccountId, SubjectId stationAccountId,
                              SubjectId destinationAccountId, int completedWorkTicks,
                              Optional<BakeryPhysicalStep> pendingPhysicalStep,
                              Optional<BakeryWorkBlock> block) {
    public enum Phase {
        DEPOT_PICKUP(1), STATION_LOAD(2), PROCESSING(3), STATION_UNLOAD(4), DEPOT_DELIVERY(5), DELIVERED(6);

        private final int wireTag;
        Phase(int wireTag) { this.wireTag = wireTag; }
        public int wireTag() { return wireTag; }
        public static Phase fromWireTag(int tag) {
            for (Phase phase : values()) if (phase.wireTag == tag) return phase;
            throw new IllegalArgumentException("unknown bakery phase tag: " + tag);
        }
    }

    public BakeryWorkState {
        Objects.requireNonNull(phase, "bakery phase");
        Objects.requireNonNull(stationId, "bakery station");
        Objects.requireNonNull(sourceAccountId, "bakery source account");
        Objects.requireNonNull(actorAccountId, "bakery actor account");
        Objects.requireNonNull(stationAccountId, "bakery station account");
        Objects.requireNonNull(destinationAccountId, "bakery destination account");
        pendingPhysicalStep = Objects.requireNonNull(pendingPhysicalStep, "bakery pending physical effect");
        block = Objects.requireNonNull(block, "bakery local block");
        if (sourceAccountId.equals(actorAccountId) || sourceAccountId.equals(stationAccountId)
                || actorAccountId.equals(stationAccountId) || actorAccountId.equals(destinationAccountId)
                || stationAccountId.equals(destinationAccountId) || completedWorkTicks < 0
                || completedWorkTicks > ProductionWorkProgress.REQUIRED_PROCESSING_TICKS
                || phase != Phase.PROCESSING && completedWorkTicks != 0) {
            throw new IllegalArgumentException("bakery work must retain distinct custody accounts and a bounded station clock");
        }
        if (pendingPhysicalStep.isPresent() && (phase == Phase.DELIVERED
                || pendingPhysicalStep.orElseThrow().phase() != phase
                || phase == Phase.PROCESSING && completedWorkTicks != ProductionWorkProgress.REQUIRED_PROCESSING_TICKS))
            throw new IllegalArgumentException("bakery pending effect must belong to its ready current phase");
        if (phase == Phase.DELIVERED && block.isPresent())
            throw new IllegalArgumentException("delivered bakery job cannot be blocked");
    }

    public BakeryWorkState(Phase phase, SubjectId stationId, SubjectId sourceAccountId,
                           SubjectId actorAccountId, SubjectId stationAccountId,
                           SubjectId destinationAccountId, int completedWorkTicks) {
        this(phase, stationId, sourceAccountId, actorAccountId, stationAccountId,
                destinationAccountId, completedWorkTicks, Optional.empty(), Optional.empty());
    }

    public BakeryWorkState(Phase phase, SubjectId stationId, SubjectId sourceAccountId,
                           SubjectId actorAccountId, SubjectId stationAccountId,
                           SubjectId destinationAccountId, int completedWorkTicks,
                           Optional<BakeryPhysicalStep> pendingPhysicalStep) {
        this(phase, stationId, sourceAccountId, actorAccountId, stationAccountId,
                destinationAccountId, completedWorkTicks, pendingPhysicalStep, Optional.empty());
    }

    public BakeryWorkState withBlock(Optional<BakeryWorkBlock> next) {
        if (phase == Phase.DELIVERED && next.isPresent())
            throw new IllegalArgumentException("delivered bakery job cannot be blocked");
        return new BakeryWorkState(phase, stationId, sourceAccountId, actorAccountId,
                stationAccountId, destinationAccountId, completedWorkTicks, pendingPhysicalStep, next);
    }

    public BakeryWorkState withReallocatedDepotAccount(SubjectId accountId) {
        if (phase != Phase.DEPOT_PICKUP || pendingPhysicalStep.isPresent()
                || block.map(value -> value.reason() != BakeryWorkBlock.Reason.SOURCE_CHANGED).orElse(true))
            throw new IllegalArgumentException("bakery depot source can change only while awaiting input");
        return new BakeryWorkState(phase, stationId, Objects.requireNonNull(accountId, "replacement depot account"),
                actorAccountId, stationAccountId, accountId, completedWorkTicks, pendingPhysicalStep, Optional.empty());
    }

    public BakeryWorkState preparePhysical(BakeryPhysicalStep step) {
        if (pendingPhysicalStep.isPresent() || block.isPresent() || step.phase() != phase)
            throw new IllegalArgumentException("bakery phase already has a physical effect intent");
        return new BakeryWorkState(phase, stationId, sourceAccountId, actorAccountId,
                stationAccountId, destinationAccountId, completedWorkTicks, Optional.of(step), block);
    }

    public BakeryWorkState withoutPhysical() {
        return new BakeryWorkState(phase, stationId, sourceAccountId, actorAccountId,
                stationAccountId, destinationAccountId, completedWorkTicks, Optional.empty(), block);
    }

    public BakeryWorkState advance(Phase next) {
        boolean legal = switch (phase) {
            case DEPOT_PICKUP -> next == Phase.STATION_LOAD;
            case STATION_LOAD -> next == Phase.PROCESSING;
            case PROCESSING -> next == Phase.STATION_UNLOAD && completedWorkTicks == ProductionWorkProgress.REQUIRED_PROCESSING_TICKS;
            case STATION_UNLOAD -> next == Phase.DEPOT_DELIVERY;
            case DEPOT_DELIVERY -> next == Phase.DELIVERED;
            case DELIVERED -> false;
        };
        if (!legal || pendingPhysicalStep.isPresent() || block.isPresent()) throw new IllegalArgumentException("bakery phase cannot skip a custody receipt or local block");
        return new BakeryWorkState(next, stationId, sourceAccountId, actorAccountId,
                stationAccountId, destinationAccountId, 0);
    }

    public BakeryWorkState workTick() {
        if (phase != Phase.PROCESSING || pendingPhysicalStep.isPresent() || block.isPresent()
                || completedWorkTicks >= ProductionWorkProgress.REQUIRED_PROCESSING_TICKS)
            throw new IllegalArgumentException("bakery work tick requires loaded station input and an open clock");
        return new BakeryWorkState(phase, stationId, sourceAccountId, actorAccountId,
                stationAccountId, destinationAccountId, completedWorkTicks + 1);
    }
}
