package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;

/** Pure validation and canonical receipt for one exact human-equipment issue boundary. */
public final class EquipmentIssueStateSupport {
    private EquipmentIssueStateSupport() { }

    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EQUIPMENT_ISSUE) throw new IllegalArgumentException("not an equipment issue intent");
        if (intent.roles().schema() == PhysicalIntentRoleSchema.ENGINEERING_EQUIPMENT_ISSUE) {
            EngineeringEquipmentStateSupport.validateIssue(state, intent);
            return;
        }
        if (intent.roles().schema() != PhysicalIntentRoleSchema.ASSAULT_EQUIPMENT_ISSUE) throw new IllegalArgumentException("equipment issue has foreign role schema");
        SubjectId assaultId = intent.roles().require(PhysicalIntentSubjectRole.SETTLEMENT_ASSAULT);
        SubjectId residentId = intent.roles().require(PhysicalIntentSubjectRole.ASSAULT_DEFENDER);
        SubjectId itemId = intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT);
        SettlementAssault assault = state.strategicPlans().settlementAssaults().get(assaultId);
        ExactItemStack item = state.inventory().items().get(itemId);
        if (assault == null || assault.status() == SettlementAssaultStatus.RESOLVED || !assault.defenderIds().contains(residentId)
                || !intent.causeSubjectId().equals(assault.settlementId()) || item == null
                || !(item.custody() instanceof InventoryCustody.ContainerSlot slot)
                || !slot.containerId().equals(FrontierWorldState.depotId(assault.settlementId()))
                || !item.economicOwnerId().equals(assault.settlementId())
                || !HumanTacticalFunctionProjection.isGrayboxWeaponKind(item.itemKind())
                || state.inventory().surfaces().get(slot.containerId()) == null
                || state.inventory().surfaces().get(slot.containerId()).status() != ContainerSurfaceStatus.ACTIVE
                || state.inventory().actorItems(residentId).stream().anyMatch(held -> held.itemKind().equals(item.itemKind()))) {
            throw new IllegalArgumentException("equipment issue lacks an active exact defender/depot/item precondition");
        }
    }

    /**
     * A receipt is immutable evidence of the completed hand-off, not a perpetual custody claim.
     * The exact stack may subsequently be returned, dropped, picked up or destroyed. Those later
     * transitions must not invalidate this historical evidence during snapshot/WAL recovery.
     */
    public static void validateReceiptForRecovery(ExactInventory inventory, PhysicalIntent intent, EquipmentIssueObservation receipt) {
        if (intent.kind() != PhysicalIntentKind.EQUIPMENT_ISSUE || !(intent.roles().equals(PhysicalIntentRoleBinding.engineeringEquipmentIssue(receipt.ownerId(), receipt.residentId(), receipt.itemId()))
                || intent.roles().equals(PhysicalIntentRoleBinding.assaultEquipmentIssue(receipt.ownerId(), receipt.residentId(), receipt.itemId())))) {
            throw new IllegalArgumentException("equipment issue receipt has foreign exact subjects");
        }
    }

    static EquipmentIssueObservation requireReceipt(PhysicalEffectObservation evidence) {
        if (evidence instanceof EquipmentIssueObservation issue) return issue;
        throw new IllegalArgumentException("equipment issue requires exact hand-off evidence");
    }

    public static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, EquipmentIssueObservation receipt,
                                              java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> nextIntents) {
        validateIntent(state, intent);
        SubjectId ownerId = ownerId(intent), residentId = residentId(intent), itemId = intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT);
        ExactItemStack item = state.inventory().items().get(itemId);
        if (!receipt.ownerId().equals(ownerId) || !receipt.residentId().equals(residentId) || !receipt.itemId().equals(itemId)
                || !receipt.sourceSlot().equals(item.custody())) throw new IllegalArgumentException("equipment issue receipt does not match exact intent");
        nextIntents.put(intent.id(), intent.withStatus(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt.id())));
        java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new java.util.LinkedHashMap<>(state.physicalObservations());
        observations.put(receipt.id(), receipt);
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().moveObservedItem(itemId, receipt.sourceSlot(), new InventoryCustody.Actor(residentId)))
                .physicalIntents(nextIntents).physicalObservations(observations));
    }

    private static SubjectId ownerId(PhysicalIntent intent) {
        return switch (intent.roles().schema()) {
            case ENGINEERING_EQUIPMENT_ISSUE -> intent.roles().require(PhysicalIntentSubjectRole.ENGINEERING_WORK_ORDER);
            case ASSAULT_EQUIPMENT_ISSUE -> intent.roles().require(PhysicalIntentSubjectRole.SETTLEMENT_ASSAULT);
            default -> throw new IllegalArgumentException("equipment issue has foreign role schema");
        };
    }

    private static SubjectId residentId(PhysicalIntent intent) {
        return switch (intent.roles().schema()) {
            case ENGINEERING_EQUIPMENT_ISSUE -> intent.roles().require(PhysicalIntentSubjectRole.ENGINEERING_WORKER);
            case ASSAULT_EQUIPMENT_ISSUE -> intent.roles().require(PhysicalIntentSubjectRole.ASSAULT_DEFENDER);
            default -> throw new IllegalArgumentException("equipment issue has foreign role schema");
        };
    }
}
