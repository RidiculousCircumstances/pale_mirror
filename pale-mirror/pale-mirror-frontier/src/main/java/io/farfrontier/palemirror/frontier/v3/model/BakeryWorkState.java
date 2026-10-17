package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Durable bakery phase and the exact custody-account identities used by one bread job. */
public record BakeryWorkState(Phase phase, SubjectId stationId, ProductionRecipe.Id recipeId,
                              String inputItemKind, int inputQuantity,
                              SubjectId sourceAccountId, SubjectId sourceContainerId,
                              SubjectId actorAccountId, SubjectId stationAccountId,
                              SubjectId destinationAccountId,
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
        Objects.requireNonNull(recipeId, "declared production recipe");
        ProductionRecipe.requireItemKey(inputItemKind);
        if (inputQuantity < 1 || inputQuantity > 64) throw new IllegalArgumentException("invalid retained production input quantity");
        Objects.requireNonNull(sourceAccountId, "bakery source account");
        Objects.requireNonNull(sourceContainerId, "retained bakery source storage");
        Objects.requireNonNull(actorAccountId, "bakery actor account");
        Objects.requireNonNull(stationAccountId, "bakery station account");
        Objects.requireNonNull(destinationAccountId, "bakery destination account");
        pendingPhysicalStep = Objects.requireNonNull(pendingPhysicalStep, "bakery pending physical effect");
        block = Objects.requireNonNull(block, "bakery local block");
        if (sourceAccountId.equals(actorAccountId)
                || actorAccountId.equals(stationAccountId) || actorAccountId.equals(destinationAccountId)
                || stationAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("bakery work must retain distinct custody accounts and a bounded station clock");
        }
        if (pendingPhysicalStep.isPresent() && (phase == Phase.DELIVERED
                || pendingPhysicalStep.orElseThrow().phase() != phase))
            throw new IllegalArgumentException("bakery pending effect must belong to its ready current phase");
        if (phase == Phase.DELIVERED && block.isPresent())
            throw new IllegalArgumentException("delivered bakery job cannot be blocked");
    }

    public BakeryWorkState(Phase phase, SubjectId stationId, ProductionRecipe.Id recipeId,
                           String inputItemKind, int inputQuantity,
                           SubjectId sourceAccountId, SubjectId sourceContainerId,
                           SubjectId actorAccountId, SubjectId stationAccountId,
                           SubjectId destinationAccountId) {
        this(phase, stationId, recipeId, inputItemKind, inputQuantity, sourceAccountId, sourceContainerId, actorAccountId, stationAccountId,
                destinationAccountId, Optional.empty(), Optional.empty());
    }

    public BakeryWorkState withBlock(Optional<BakeryWorkBlock> next) {
        if (phase == Phase.DELIVERED && next.isPresent())
            throw new IllegalArgumentException("delivered bakery job cannot be blocked");
        return new BakeryWorkState(phase, stationId, recipeId, inputItemKind, inputQuantity, sourceAccountId, sourceContainerId, actorAccountId,
                stationAccountId, destinationAccountId, pendingPhysicalStep, next);
    }

    /** After the full hand is deposited, an unbegun order may select a smaller
     * recipe portion. Its surplus remains physically at the input port. */
    public BakeryWorkState withLoadedQuantity(int quantity) {
        if (phase != Phase.STATION_LOAD || pendingPhysicalStep.isPresent() || block.isPresent()
                || quantity < 1 || quantity > inputQuantity)
            throw new IllegalArgumentException("only a settled unbegun station load may select its recipe portion");
        return new BakeryWorkState(phase, stationId, recipeId, inputItemKind, quantity, sourceAccountId,
                sourceContainerId, actorAccountId, stationAccountId, destinationAccountId);
    }

    public BakeryWorkState withDestination(SubjectId account) {
        if (pendingPhysicalStep.isPresent() || phase == Phase.DELIVERED)
            throw new IllegalArgumentException("bakery destination cannot change across a pending or completed delivery");
        return new BakeryWorkState(phase, stationId, recipeId, inputItemKind, inputQuantity, sourceAccountId, sourceContainerId, actorAccountId,
                stationAccountId, account, Optional.empty(),
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
        return new BakeryWorkState(phase, stationId, recipeId, inputItemKind, inputQuantity, Objects.requireNonNull(accountId, "replacement depot account"),
                Objects.requireNonNull(containerId, "replacement source storage"), actorAccountId, stationAccountId, destinationAccountId, pendingPhysicalStep, Optional.empty());
    }

    public BakeryWorkState preparePhysical(BakeryPhysicalStep step) {
        if (pendingPhysicalStep.isPresent() || block.isPresent() || step.phase() != phase)
            throw new IllegalArgumentException("bakery phase already has a physical effect intent");
        return new BakeryWorkState(phase, stationId, recipeId, inputItemKind, inputQuantity, sourceAccountId, sourceContainerId, actorAccountId,
                stationAccountId, destinationAccountId, Optional.of(step), block);
    }

    public BakeryWorkState withoutPhysical() {
        return new BakeryWorkState(phase, stationId, recipeId, inputItemKind, inputQuantity, sourceAccountId, sourceContainerId, actorAccountId,
                stationAccountId, destinationAccountId, Optional.empty(), block);
    }
    public BakeryWorkState withReplacementStation(ProductionStationSpec replacement) {
        if (pendingPhysicalStep.isPresent() || phase != Phase.DEPOT_PICKUP && phase != Phase.STATION_LOAD)
            throw new IllegalArgumentException("station replacement cannot cross a pending or loaded operation");
        return new BakeryWorkState(phase, replacement.id(), recipeId, inputItemKind, inputQuantity, sourceAccountId, sourceContainerId,
                actorAccountId, ReferenceContainerCustody.scopeId(replacement.containerId()), destinationAccountId, Optional.empty(), Optional.empty());
    }

    public BakeryWorkState advance(Phase next) {
        boolean legal = switch (phase) {
            case DEPOT_PICKUP -> next == Phase.STATION_LOAD;
            case STATION_LOAD -> next == Phase.PROCESSING;
            case PROCESSING -> next == Phase.STATION_UNLOAD; // The station owner confirms its operation completion.
            case STATION_UNLOAD -> next == Phase.DEPOT_DELIVERY;
            case DEPOT_DELIVERY -> next == Phase.DELIVERED;
            case DELIVERED -> false;
        };
        if (!legal || pendingPhysicalStep.isPresent() || block.isPresent()) throw new IllegalArgumentException("bakery phase cannot skip a custody receipt or local block");
        return new BakeryWorkState(next, stationId, recipeId, inputItemKind, inputQuantity, sourceAccountId, sourceContainerId, actorAccountId,
                stationAccountId, destinationAccountId);
    }

}
