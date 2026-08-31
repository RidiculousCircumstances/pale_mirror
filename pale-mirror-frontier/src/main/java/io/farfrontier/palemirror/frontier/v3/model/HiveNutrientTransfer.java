package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

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
                                  Optional<HiveNutrientTransferBlockReason> blockReason) {
    public static final int MAX_CORRIDOR_NODES = 256;

    public HiveNutrientTransfer {
        Objects.requireNonNull(id, "hive nutrient transfer id"); Objects.requireNonNull(hiveId, "hive id");
        Objects.requireNonNull(requesterTaskId, "requester task id"); Objects.requireNonNull(sourceStoreId, "source store id");
        Objects.requireNonNull(sourceSlot, "source slot"); Objects.requireNonNull(targetStoreId, "target store id");
        Objects.requireNonNull(targetSlot, "target slot"); Objects.requireNonNull(cargoId, "hive nutrient cargo id");
        Objects.requireNonNull(itemId, "hive nutrient item id"); corridor = List.copyOf(corridor);
        Objects.requireNonNull(phase, "hive nutrient phase"); Objects.requireNonNull(blockReason, "hive nutrient block reason");
        if (sourceStoreId.equals(targetStoreId) || !sourceSlot.containerId().equals(sourceStoreId)
                || !targetSlot.containerId().equals(targetStoreId) || sourceSlot.slot() < 0 || targetSlot.slot() < 0
                || corridor.size() < 2 || corridor.size() > MAX_CORRIDOR_NODES || cursor < 0 || cursor >= corridor.size()) {
            throw new IllegalArgumentException("hive nutrient transfer has an invalid bounded corridor or socket");
        }
        if (phase == HiveNutrientTransferPhase.BLOCKED != blockReason.isPresent()) {
            throw new IllegalArgumentException("only a blocked hive nutrient transfer records a block reason");
        }
    }

    HiveNutrientTransfer advanceTo(int nextCursor) {
        if (phase != HiveNutrientTransferPhase.IN_TRANSIT || nextCursor <= cursor || nextCursor >= corridor.size()) {
            throw new IllegalArgumentException("hive nutrient transfer cannot advance to that cursor");
        }
        return new HiveNutrientTransfer(id, hiveId, requesterTaskId, sourceStoreId, sourceSlot, targetStoreId, targetSlot,
                cargoId, itemId, corridor, nextCursor, phase, blockReason);
    }

    HiveNutrientTransfer block(HiveNutrientTransferBlockReason reason) {
        if (phase != HiveNutrientTransferPhase.IN_TRANSIT) throw new IllegalArgumentException("hive nutrient transfer is already terminal");
        return new HiveNutrientTransfer(id, hiveId, requesterTaskId, sourceStoreId, sourceSlot, targetStoreId, targetSlot,
                cargoId, itemId, corridor, cursor, HiveNutrientTransferPhase.BLOCKED, Optional.of(Objects.requireNonNull(reason, "block reason")));
    }
}
