package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;

/** Pure validation and canonical receipt for one exact human-equipment return boundary. */
public final class EquipmentReturnStateSupport {
    private EquipmentReturnStateSupport() { }

    public static InventoryCustody.ContainerSlot targetSlot(FrontierWorldState state, PhysicalIntent intent) {
        return EquipmentServiceBoundary.targetSlot(intent);
    }

    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() != PhysicalIntentKind.EQUIPMENT_RETURN) throw new IllegalArgumentException("foreign equipment intent kind");
        EquipmentServiceBoundary.validateIntent(state, intent);
    }

    public static void validateReceiptForRecovery(ExactInventory inventory, PhysicalIntent intent, EquipmentReturnObservation receipt) {
        var declaration = EquipmentServiceBoundary.capability(intent);
        if (intent.kind() != PhysicalIntentKind.EQUIPMENT_RETURN
                || !declaration.ownerId(intent).equals(receipt.ownerId()) || !declaration.actorId(intent).equals(receipt.residentId())
                || !intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT).equals(receipt.itemId())
                || !EquipmentPhysicalAuthority.container(intent).equals(receipt.targetSlot().containerId())
                || !EquipmentServiceBoundary.targetSlot(intent).equals(receipt.targetSlot())) {
            throw new IllegalArgumentException("equipment return receipt has foreign exact subjects/container");
        }
    }

    static EquipmentReturnObservation requireReceipt(PhysicalEffectObservation evidence) {
        if (evidence instanceof EquipmentReturnObservation returned) return returned;
        throw new IllegalArgumentException("equipment return requires exact hand-off evidence");
    }

    public static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, EquipmentReturnObservation receipt,
                                              java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId, PhysicalIntent> nextIntents) {
        if (intent.kind() != PhysicalIntentKind.EQUIPMENT_RETURN) throw new IllegalArgumentException("foreign equipment receipt kind");
        EquipmentServiceBoundary.validateInspection(state, intent);
        SubjectId ownerId = ownerId(intent), residentId = residentId(intent), itemId = intent.roles().require(PhysicalIntentSubjectRole.EQUIPMENT);
        ExactItemStack item = state.inventory().items().get(itemId); InventoryCustody.ContainerSlot target = targetSlot(state, intent);
        if (!receipt.ownerId().equals(ownerId) || !receipt.residentId().equals(residentId) || !receipt.itemId().equals(itemId)
                || !receipt.targetSlot().equals(target) || !item.custody().equals(new InventoryCustody.Actor(residentId))) {
            throw new IllegalArgumentException("equipment return receipt does not match exact intent");
        }
        nextIntents.put(intent.id(), intent.withStatus(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.CONFIRMED, java.util.Optional.of(receipt.id())));
        java.util.Map<io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId, PhysicalEffectObservation> observations = new java.util.LinkedHashMap<>(state.physicalObservations());
        observations.put(receipt.id(), receipt);
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .inventory(state.inventory().moveObservedItem(itemId, item.custody(), target))
                .physicalIntents(nextIntents).physicalObservations(observations));
    }

    private static SubjectId ownerId(PhysicalIntent intent) { return EquipmentServiceBoundary.capability(intent).ownerId(intent); }
    private static SubjectId residentId(PhysicalIntent intent) { return EquipmentServiceBoundary.capability(intent).actorId(intent); }
}
