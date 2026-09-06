package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One exact COLD nutrient shipment across the distributed hive's retained organ graph.
 * The item keeps its identity in {@link CargoBatch}; this record owns only corridor state.
 */
public record HiveNutrientTransfer(SubjectId id, SubjectId hiveId, SubjectId requesterTaskId,
                                  SubjectId sourceStoreId, InventoryCustody.ContainerSlot sourceSlot,
                                  SubjectId targetStoreId, InventoryCustody.ContainerSlot targetSlot,
                                  SubjectId cargoId, SubjectId itemId, List<BlockPosition> corridor,
                                  int cursor, HiveNutrientTransferPhase phase,
                                  Optional<PhysicalIntentId> endpointIntentId,
                                  Optional<HiveNutrientTransferBlockReason> blockReason) {
    public static final int MAX_CORRIDOR_NODES = 256;

    public HiveNutrientTransfer {
        Objects.requireNonNull(id, "hive nutrient transfer id"); Objects.requireNonNull(hiveId, "hive id");
        Objects.requireNonNull(requesterTaskId, "requester task id"); Objects.requireNonNull(sourceStoreId, "source store id");
        Objects.requireNonNull(sourceSlot, "source slot"); Objects.requireNonNull(targetStoreId, "target store id");
        Objects.requireNonNull(targetSlot, "target slot"); Objects.requireNonNull(cargoId, "hive nutrient cargo id");
        Objects.requireNonNull(itemId, "hive nutrient item id"); corridor = List.copyOf(corridor);
        Objects.requireNonNull(phase, "hive nutrient phase"); Objects.requireNonNull(endpointIntentId, "hive nutrient endpoint intent"); Objects.requireNonNull(blockReason, "hive nutrient block reason");
        if (sourceStoreId.equals(targetStoreId) || !sourceSlot.containerId().equals(sourceStoreId)
                || !targetSlot.containerId().equals(targetStoreId) || sourceSlot.slot() < 0 || targetSlot.slot() < 0
                || corridor.size() < 2 || corridor.size() > MAX_CORRIDOR_NODES || cursor < 0 || cursor >= corridor.size()) {
            throw new IllegalArgumentException("hive nutrient transfer has an invalid bounded corridor or socket");
        }
        boolean pendingEndpoint = phase == HiveNutrientTransferPhase.DEPARTURE_PENDING || phase == HiveNutrientTransferPhase.ARRIVAL_PENDING;
        if (pendingEndpoint != endpointIntentId.isPresent() || (phase == HiveNutrientTransferPhase.BLOCKED) != blockReason.isPresent()) {
            throw new IllegalArgumentException("hive nutrient transfer phase has invalid endpoint or block evidence");
        }
    }

    public HiveNutrientTransfer advanceTo(int nextCursor) {
        if (phase != HiveNutrientTransferPhase.IN_TRANSIT || nextCursor <= cursor || nextCursor >= corridor.size()) {
            throw new IllegalArgumentException("hive nutrient transfer cannot advance to that cursor");
        }
        return new HiveNutrientTransfer(id, hiveId, requesterTaskId, sourceStoreId, sourceSlot, targetStoreId, targetSlot,
                cargoId, itemId, corridor, nextCursor, phase, endpointIntentId, blockReason);
    }

    public HiveNutrientTransfer departed() {
        if (phase != HiveNutrientTransferPhase.DEPARTURE_PENDING) throw new IllegalArgumentException("hive nutrient departure is not pending");
        return new HiveNutrientTransfer(id, hiveId, requesterTaskId, sourceStoreId, sourceSlot, targetStoreId, targetSlot,
                cargoId, itemId, corridor, cursor, HiveNutrientTransferPhase.IN_TRANSIT, Optional.empty(), Optional.empty());
    }

    public HiveNutrientTransfer awaitArrival(PhysicalIntentId intentId) {
        if (phase != HiveNutrientTransferPhase.IN_TRANSIT || cursor != corridor.size() - 1) throw new IllegalArgumentException("hive nutrient has not reached its target");
        return new HiveNutrientTransfer(id, hiveId, requesterTaskId, sourceStoreId, sourceSlot, targetStoreId, targetSlot,
                cargoId, itemId, corridor, cursor, HiveNutrientTransferPhase.ARRIVAL_PENDING, Optional.of(intentId), Optional.empty());
    }

    public HiveNutrientTransfer block(HiveNutrientTransferBlockReason reason) {
        if (phase == HiveNutrientTransferPhase.BLOCKED) throw new IllegalArgumentException("hive nutrient transfer is already terminal");
        return new HiveNutrientTransfer(id, hiveId, requesterTaskId, sourceStoreId, sourceSlot, targetStoreId, targetSlot,
                cargoId, itemId, corridor, cursor, HiveNutrientTransferPhase.BLOCKED, Optional.empty(), Optional.of(Objects.requireNonNull(reason, "block reason")));
    }
}
