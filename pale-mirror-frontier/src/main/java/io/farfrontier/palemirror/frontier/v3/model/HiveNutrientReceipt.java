package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Retained terminal receipt proving the exact nutrient reached its named destination slot. */
public record HiveNutrientReceipt(SubjectId transferId, SubjectId hiveId, SubjectId cargoId, SubjectId itemId,
                                  InventoryCustody.ContainerSlot sourceSlot, InventoryCustody.ContainerSlot targetSlot,
                                  HiveNutrientReceiptStatus status, Optional<SubjectId> consumedByJobId) {
    public HiveNutrientReceipt {
        Objects.requireNonNull(transferId, "hive nutrient transfer id"); Objects.requireNonNull(hiveId, "hive id");
        Objects.requireNonNull(cargoId, "hive nutrient cargo id"); Objects.requireNonNull(itemId, "hive nutrient item id");
        Objects.requireNonNull(sourceSlot, "source slot"); Objects.requireNonNull(targetSlot, "target slot"); Objects.requireNonNull(status, "hive nutrient receipt status");
        Objects.requireNonNull(consumedByJobId, "hive nutrient consumer");
        if (status == HiveNutrientReceiptStatus.CONSUMED != consumedByJobId.isPresent()) throw new IllegalArgumentException("only consumed nutrient receipt names its exact local work");
    }

    public HiveNutrientReceipt(SubjectId transferId, SubjectId hiveId, SubjectId cargoId, SubjectId itemId,
                        InventoryCustody.ContainerSlot sourceSlot, InventoryCustody.ContainerSlot targetSlot) {
        this(transferId, hiveId, cargoId, itemId, sourceSlot, targetSlot, HiveNutrientReceiptStatus.STORED, Optional.empty());
    }

    boolean matches(HiveNutrientTransfer transfer) {
        return transfer.id().equals(transferId) && transfer.hiveId().equals(hiveId) && transfer.cargoId().equals(cargoId)
                && transfer.itemId().equals(itemId) && transfer.sourceSlot().equals(sourceSlot) && transfer.targetSlot().equals(targetSlot);
    }

    HiveNutrientReceipt consumeBy(SubjectId jobId) {
        if (status != HiveNutrientReceiptStatus.STORED) throw new IllegalArgumentException("hive nutrient receipt is already consumed");
        return new HiveNutrientReceipt(transferId, hiveId, cargoId, itemId, sourceSlot, targetSlot, HiveNutrientReceiptStatus.CONSUMED,
                Optional.of(Objects.requireNonNull(jobId, "hive growth job id")));
    }
}
