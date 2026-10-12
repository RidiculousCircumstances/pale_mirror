package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;

/** Pure validation and canonical receipt for one exact human-equipment issue boundary. */
public final class EquipmentIssueStateSupport {
    private EquipmentIssueStateSupport() { }

    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EQUIPMENT_ISSUE) throw new IllegalArgumentException("foreign equipment intent kind");
        EquipmentServiceBoundary.validateIntent(state, intent);
    }

    /**
     * A receipt is immutable evidence of the completed hand-off, not a perpetual custody claim.
     * The exact stack may subsequently be returned, dropped, picked up or destroyed. Those later
     * transitions must not invalidate this historical evidence during snapshot/WAL recovery.
     */
    public static void validateReceiptForRecovery(ExactInventory inventory, PhysicalIntent intent, EquipmentIssueObservation receipt) {
        var declaration = EquipmentServiceBoundary.capability(intent);
        if (intent.kind() != PhysicalIntentKind.EQUIPMENT_ISSUE
                || !declaration.ownerId(intent).equals(receipt.ownerId()) || !declaration.actorId(intent).equals(receipt.residentId())
                || !intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT).equals(receipt.itemId())
                || !EquipmentPhysicalAuthority.container(intent).equals(receipt.sourceSlot().containerId())) {
            throw new IllegalArgumentException("equipment issue receipt has foreign exact subjects/container");
        }
    }

    static EquipmentIssueObservation requireReceipt(PhysicalEffectObservation evidence) {
        if (evidence instanceof EquipmentIssueObservation issue) return issue;
        throw new IllegalArgumentException("equipment issue requires exact hand-off evidence");
    }

    public static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, EquipmentIssueObservation receipt,
                                              java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> nextIntents) {
        if (intent.kind() != PhysicalIntentKind.EQUIPMENT_ISSUE) throw new IllegalArgumentException("foreign equipment receipt kind");
        EquipmentServiceBoundary.validateInspection(state, intent);
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

    private static SubjectId ownerId(PhysicalIntent intent) { return EquipmentServiceBoundary.capability(intent).ownerId(intent); }
    private static SubjectId residentId(PhysicalIntent intent) { return EquipmentServiceBoundary.capability(intent).actorId(intent); }
}
