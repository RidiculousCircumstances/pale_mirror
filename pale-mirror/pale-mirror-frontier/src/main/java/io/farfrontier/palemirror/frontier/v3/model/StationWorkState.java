package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Shared station fulfillment phase and retained resource endpoints; the recipe caller owns demand. */
public record StationWorkState(Phase phase, ResidentWorkKind permission, StationWorkProduct.Representation outputRepresentation, Optional<MaintenanceTarget> maintenanceTarget,
                              SubjectId stationId, ProductionRecipe.Id recipeId,
                              String inputItemKind, int inputQuantity,
                              SubjectId sourceAccountId, SubjectId sourceContainerId,
                              SubjectId actorAccountId, SubjectId stationAccountId,
                              SubjectId destinationAccountId,
                              Optional<StationWorkPhysicalStep> pendingPhysicalStep,
                              Optional<BakeryWorkBlock> block) {
    public record MaintenanceTarget(SubjectId itemId, SubjectId sourceContainerId, String itemKind) {
        public MaintenanceTarget {
            Objects.requireNonNull(itemId, "maintenance item");
            ProductionRecipe.requireItemKey(itemKind);
            Objects.requireNonNull(sourceContainerId, "maintenance source");
        }
    }
    public enum Phase {
        DEPOT_PICKUP(1), STATION_LOAD(2), PROCESSING(3), STATION_UNLOAD(4), DEPOT_DELIVERY(5), DELIVERED(6), TARGET_PICKUP(7), TARGET_LOAD(8);

        private final int wireTag;
        Phase(int wireTag) { this.wireTag = wireTag; }
        public int wireTag() { return wireTag; }
        public boolean maintenanceTransfer() { return this == TARGET_PICKUP || this == TARGET_LOAD; }
        public boolean inputAtSource() { return this == DEPOT_PICKUP || maintenanceTransfer(); }
        public boolean sourcePickup() { return this == DEPOT_PICKUP || this == TARGET_PICKUP; }
        public boolean carriesCargo() { return this == STATION_LOAD || this == TARGET_LOAD || this == DEPOT_DELIVERY; }
        public static Phase fromWireTag(int tag) {
            for (Phase phase : values()) if (phase.wireTag == tag) return phase;
            throw new IllegalArgumentException("unknown bakery phase tag: " + tag);
        }
    }

    public StationWorkState {
        Objects.requireNonNull(phase, "station phase");
        Objects.requireNonNull(permission, "declared work permission");
        Objects.requireNonNull(outputRepresentation, "declared product representation");
        maintenanceTarget = Objects.requireNonNull(maintenanceTarget, "declared maintenance target");
        if ((phase == Phase.TARGET_PICKUP || phase == Phase.TARGET_LOAD) && maintenanceTarget.isEmpty())
            throw new IllegalArgumentException("maintenance transfer has no retained target");
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

    public StationWorkState(Phase phase, ResidentWorkKind permission, StationWorkProduct.Representation outputRepresentation, Optional<MaintenanceTarget> maintenanceTarget,
                           SubjectId stationId, ProductionRecipe.Id recipeId,
                           String inputItemKind, int inputQuantity,
                           SubjectId sourceAccountId, SubjectId sourceContainerId,
                           SubjectId actorAccountId, SubjectId stationAccountId,
                           SubjectId destinationAccountId) {
        this(phase, permission, outputRepresentation, maintenanceTarget, stationId, recipeId, inputItemKind, inputQuantity, sourceAccountId, sourceContainerId, actorAccountId, stationAccountId,
                destinationAccountId, Optional.empty(), Optional.empty());
    }

    public StationWorkState withBlock(Optional<BakeryWorkBlock> next) {
        if (phase == Phase.DELIVERED && next.isPresent())
            throw new IllegalArgumentException("delivered bakery job cannot be blocked");
        return new StationWorkState(phase, permission, outputRepresentation, maintenanceTarget, stationId, recipeId, inputItemKind, inputQuantity, sourceAccountId, sourceContainerId, actorAccountId,
                stationAccountId, destinationAccountId, pendingPhysicalStep, next);
    }

    /** After the full hand is deposited, an unbegun order may select a smaller
     * recipe portion. Its surplus remains physically at the input port. */
    public StationWorkState withLoadedQuantity(int quantity) {
        if (phase != Phase.STATION_LOAD || pendingPhysicalStep.isPresent() || block.isPresent()
                || quantity < 1 || quantity > inputQuantity)
            throw new IllegalArgumentException("only a settled unbegun station load may select its recipe portion");
        return new StationWorkState(phase, permission, outputRepresentation, maintenanceTarget, stationId, recipeId, inputItemKind, quantity, sourceAccountId,
                sourceContainerId, actorAccountId, stationAccountId, destinationAccountId);
    }

    public StationWorkState withDestination(SubjectId account) {
        if (pendingPhysicalStep.isPresent() || phase == Phase.DELIVERED)
            throw new IllegalArgumentException("bakery destination cannot change across a pending or completed delivery");
        return new StationWorkState(phase, permission, outputRepresentation, maintenanceTarget, stationId, recipeId, inputItemKind, inputQuantity, sourceAccountId, sourceContainerId, actorAccountId,
                stationAccountId, account, Optional.empty(),
                block.filter(value -> value.reason() != BakeryWorkBlock.Reason.DESTINATION_OCCUPIED));
    }

    /** The reservation ends at confirmed output removal, not delivery or the worker's return. */
    public boolean reservesStation() {
        return switch (phase) {
            case TARGET_PICKUP, TARGET_LOAD, DEPOT_PICKUP, STATION_LOAD, PROCESSING, STATION_UNLOAD -> true;
            case DEPOT_DELIVERY, DELIVERED -> false;
        };
    }

    public StationWorkState withReallocatedDepotAccount(SubjectId accountId, SubjectId containerId) {
        if (!phase.inputAtSource() || pendingPhysicalStep.isPresent()
                || block.map(value -> value.reason() != BakeryWorkBlock.Reason.SOURCE_CHANGED).orElse(true))
            throw new IllegalArgumentException("bakery depot source can change only while awaiting input");
        return new StationWorkState(phase, permission, outputRepresentation, maintenanceTarget, stationId, recipeId, inputItemKind, inputQuantity, Objects.requireNonNull(accountId, "replacement depot account"),
                Objects.requireNonNull(containerId, "replacement source storage"), actorAccountId, stationAccountId, destinationAccountId, pendingPhysicalStep, Optional.empty());
    }

    public StationWorkState preparePhysical(StationWorkPhysicalStep step) {
        if (pendingPhysicalStep.isPresent() || block.isPresent() || step.phase() != phase)
            throw new IllegalArgumentException("bakery phase already has a physical effect intent");
        return new StationWorkState(phase, permission, outputRepresentation, maintenanceTarget, stationId, recipeId, inputItemKind, inputQuantity, sourceAccountId, sourceContainerId, actorAccountId,
                stationAccountId, destinationAccountId, Optional.of(step), block);
    }

    public StationWorkState withoutPhysical() {
        return new StationWorkState(phase, permission, outputRepresentation, maintenanceTarget, stationId, recipeId, inputItemKind, inputQuantity, sourceAccountId, sourceContainerId, actorAccountId,
                stationAccountId, destinationAccountId, Optional.empty(), block);
    }
    public StationWorkState withReplacementStation(ProductionStationSpec replacement) {
        if (pendingPhysicalStep.isPresent() || phase != Phase.DEPOT_PICKUP && phase != Phase.STATION_LOAD)
            throw new IllegalArgumentException("station replacement cannot cross a pending or loaded operation");
        return new StationWorkState(phase, permission, outputRepresentation, maintenanceTarget, replacement.id(), recipeId, inputItemKind, inputQuantity, sourceAccountId, sourceContainerId,
                actorAccountId, ReferenceContainerCustody.scopeId(replacement.containerId()), destinationAccountId, Optional.empty(), Optional.empty());
    }

    public StationWorkState advance(Phase next) {
        boolean legal = switch (phase) {
            case TARGET_PICKUP -> next == Phase.TARGET_LOAD;
            case TARGET_LOAD -> next == Phase.DEPOT_PICKUP;
            case DEPOT_PICKUP -> next == Phase.STATION_LOAD;
            case STATION_LOAD -> next == Phase.PROCESSING;
            case PROCESSING -> next == Phase.STATION_UNLOAD; // The station owner confirms its operation completion.
            case STATION_UNLOAD -> next == Phase.DEPOT_DELIVERY;
            case DEPOT_DELIVERY -> next == Phase.DELIVERED;
            case DELIVERED -> false;
        };
        if (!legal || pendingPhysicalStep.isPresent() || block.isPresent()) throw new IllegalArgumentException("bakery phase cannot skip a custody receipt or local block");
        return new StationWorkState(next, permission, outputRepresentation, maintenanceTarget, stationId, recipeId, inputItemKind, inputQuantity, sourceAccountId, sourceContainerId, actorAccountId,
                stationAccountId, destinationAccountId);
    }

}
