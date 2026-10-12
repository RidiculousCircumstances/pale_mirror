package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Durable bakery phase and the exact custody-account identities used by one bread job. */
public record BakeryWorkState(Phase phase, SubjectId stationId, SubjectId sourceAccountId, SubjectId sourceContainerId,
                              SubjectId actorAccountId, SubjectId stationAccountId,
                              SubjectId destinationAccountId, Optional<WorkProgress> labour,
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
        Objects.requireNonNull(sourceContainerId, "retained bakery source storage");
        Objects.requireNonNull(actorAccountId, "bakery actor account");
        Objects.requireNonNull(stationAccountId, "bakery station account");
        Objects.requireNonNull(destinationAccountId, "bakery destination account");
        labour = Objects.requireNonNull(labour, "bakery labour");
        pendingPhysicalStep = Objects.requireNonNull(pendingPhysicalStep, "bakery pending physical effect");
        block = Objects.requireNonNull(block, "bakery local block");
        if (sourceAccountId.equals(actorAccountId) || sourceAccountId.equals(stationAccountId)
                || actorAccountId.equals(stationAccountId) || actorAccountId.equals(destinationAccountId)
                || stationAccountId.equals(destinationAccountId)
                || phase != Phase.PROCESSING && labour.isPresent()) {
            throw new IllegalArgumentException("bakery work must retain distinct custody accounts and a bounded station clock");
        }
        if (pendingPhysicalStep.isPresent() && (phase == Phase.DELIVERED
                || pendingPhysicalStep.orElseThrow().phase() != phase
                || phase == Phase.PROCESSING && labour.filter(WorkProgress::complete).isEmpty()))
            throw new IllegalArgumentException("bakery pending effect must belong to its ready current phase");
        if (phase == Phase.DELIVERED && block.isPresent())
            throw new IllegalArgumentException("delivered bakery job cannot be blocked");
    }

    public BakeryWorkState(Phase phase, SubjectId stationId, SubjectId sourceAccountId, SubjectId sourceContainerId,
                           SubjectId actorAccountId, SubjectId stationAccountId,
                           SubjectId destinationAccountId, Optional<WorkProgress> labour) {
        this(phase, stationId, sourceAccountId, sourceContainerId, actorAccountId, stationAccountId,
                destinationAccountId, labour, Optional.empty(), Optional.empty());
    }

    public boolean labourComplete() { return labour.filter(WorkProgress::complete).isPresent(); }

    /** Read-only percentage; no invocation counter remains as work authority. */
    public int labourPermille() {
        return labour.map(value -> (int) (value.completedMilliWork() * 1_000L / value.requiredMilliWork())).orElse(0);
    }

    public BakeryWorkState withLabour(WorkProgress next) {
        if (phase != Phase.PROCESSING || pendingPhysicalStep.isPresent())
            throw new IllegalArgumentException("bakery labour cannot change across a physical recipe");
        return new BakeryWorkState(phase, stationId, sourceAccountId, sourceContainerId, actorAccountId,
                stationAccountId, destinationAccountId, Optional.of(next), pendingPhysicalStep, block);
    }

    public BakeryWorkState withBlock(Optional<BakeryWorkBlock> next) {
        if (phase == Phase.DELIVERED && next.isPresent())
            throw new IllegalArgumentException("delivered bakery job cannot be blocked");
        return new BakeryWorkState(phase, stationId, sourceAccountId, sourceContainerId, actorAccountId,
                stationAccountId, destinationAccountId, labour, pendingPhysicalStep, next);
    }

    public BakeryWorkState withDestination(SubjectId account) {
        if (pendingPhysicalStep.isPresent() || phase == Phase.DELIVERED)
            throw new IllegalArgumentException("bakery destination cannot change across a pending or completed delivery");
        return new BakeryWorkState(phase, stationId, sourceAccountId, sourceContainerId, actorAccountId,
                stationAccountId, account, labour, Optional.empty(),
                block.filter(value -> value.reason() != BakeryWorkBlock.Reason.DESTINATION_OCCUPIED));
    }

    /** The reservation ends at confirmed output removal, not delivery or the worker's return. */
    public boolean reservesStation() {
        return switch (phase) {
            case DEPOT_PICKUP, STATION_LOAD, PROCESSING, STATION_UNLOAD -> true;
            case DEPOT_DELIVERY, DELIVERED -> false;
        };
    }

    public BakeryWorkState withReallocatedDepotAccount(SubjectId accountId, SubjectId containerId) {
        if (phase != Phase.DEPOT_PICKUP || pendingPhysicalStep.isPresent()
                || block.map(value -> value.reason() != BakeryWorkBlock.Reason.SOURCE_CHANGED).orElse(true))
            throw new IllegalArgumentException("bakery depot source can change only while awaiting input");
        return new BakeryWorkState(phase, stationId, Objects.requireNonNull(accountId, "replacement depot account"),
                Objects.requireNonNull(containerId, "replacement source storage"), actorAccountId, stationAccountId, destinationAccountId, labour, pendingPhysicalStep, Optional.empty());
    }

    public BakeryWorkState preparePhysical(BakeryPhysicalStep step) {
        if (pendingPhysicalStep.isPresent() || block.isPresent() || step.phase() != phase)
            throw new IllegalArgumentException("bakery phase already has a physical effect intent");
        return new BakeryWorkState(phase, stationId, sourceAccountId, sourceContainerId, actorAccountId,
                stationAccountId, destinationAccountId, labour, Optional.of(step), block);
    }

    public BakeryWorkState withoutPhysical() {
        return new BakeryWorkState(phase, stationId, sourceAccountId, sourceContainerId, actorAccountId,
                stationAccountId, destinationAccountId, labour, Optional.empty(), block);
    }

    public BakeryWorkState advance(Phase next) {
        boolean legal = switch (phase) {
            case DEPOT_PICKUP -> next == Phase.STATION_LOAD;
            case STATION_LOAD -> next == Phase.PROCESSING;
            case PROCESSING -> next == Phase.STATION_UNLOAD && labourComplete();
            case STATION_UNLOAD -> next == Phase.DEPOT_DELIVERY;
            case DEPOT_DELIVERY -> next == Phase.DELIVERED;
            case DELIVERED -> false;
        };
        if (!legal || pendingPhysicalStep.isPresent() || block.isPresent()) throw new IllegalArgumentException("bakery phase cannot skip a custody receipt or local block");
        return new BakeryWorkState(next, stationId, sourceAccountId, sourceContainerId, actorAccountId,
                stationAccountId, destinationAccountId, Optional.empty());
    }

}
